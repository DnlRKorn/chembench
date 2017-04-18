package edu.unc.ceccr.chembench.workflows.descriptors;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import edu.unc.ceccr.chembench.global.Constants;
import edu.unc.ceccr.chembench.persistence.Descriptors;
import edu.unc.ceccr.chembench.persistence.Predictor;
import edu.unc.ceccr.chembench.utilities.RunExternalProgram;
import edu.unc.ceccr.chembench.utilities.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadDescriptors {
    // Read in the output of a descriptor generation program
    // (molconnZ, dragon, etc.)
    // Create a Descriptors object for each compound.
    // puts results into descriptorNames and descriptorValueMatrix.

    private static final Logger logger = LoggerFactory.getLogger(ReadDescriptors.class);
    private static final Pattern ISIDA_HEADER_REGEX = Pattern.compile("\\s*\\d+\\.\\s*(.+)");
    private static final Pattern ISIDA_FILENAME_REGEX = Pattern.compile("(.*\\.ISIDA)(\\.svm(_\\d+)?)?");

    public static String[] readDescriptorNamesFromX(String xFile, String workingDir) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(workingDir + xFile));
        br.readLine(); // numCompounds, numDescriptors;
        String[] descs = br.readLine().split("\\s+");
        br.close();
        return descs;
    }

    public static void convertCdkToX(String cdkOutputFile, String workingDir) throws Exception {
        String cmd = "python " + Constants.CECCR_BASE_PATH + Constants.SCRIPTS_PATH + "cdkToX.py " + cdkOutputFile +
                " " + cdkOutputFile + ".x";
        RunExternalProgram.runCommandAndLogOutput(cmd, workingDir, "cdkToX.py");

        // Any errors from MolconnZ processing will be in the log files. Read
        // 'em.
    }

    public static void readDescriptors(Predictor predictor, String sdfFile, List<String> descriptorNames,
                                       List<Descriptors> descriptorValueMatrix) throws Exception {
        readDescriptors(predictor.getDescriptorGeneration(), sdfFile, descriptorNames, descriptorValueMatrix);
    }

    // Reads a single descriptor file into the descriptorValueMatrix
    public static void readDescriptors(String descriptorType, String sdfFile, List<String> descriptorNames,
                                        List<Descriptors> descriptorValueMatrix) throws Exception {
        if (descriptorType.equals(Constants.CDK)) {
            ReadDescriptors.readXDescriptors(sdfFile + ".cdk.x", descriptorNames, descriptorValueMatrix);
        } else if (descriptorType.equals(Constants.DRAGONH)) {
            ReadDescriptors.readDragonXDescriptors(sdfFile + ".dragonH", descriptorNames, descriptorValueMatrix);
        } else if (descriptorType.equals(Constants.DRAGONNOH)) {
            ReadDescriptors.readDragonXDescriptors(sdfFile + ".dragonNoH", descriptorNames, descriptorValueMatrix);
        } else if (descriptorType.equals(Constants.MOE2D)) {
            ReadDescriptors.readMoe2DDescriptors(sdfFile + ".moe2D", descriptorNames, descriptorValueMatrix);
        } else if (descriptorType.equals(Constants.MACCS)) {
            ReadDescriptors.readMaccsDescriptors(sdfFile + ".maccs", descriptorNames, descriptorValueMatrix);
        } else if (descriptorType.equals(Constants.ISIDA)) {
            ReadDescriptors.readIsidaDescriptors(sdfFile + ".ISIDA", descriptorNames, descriptorValueMatrix);
        } else if (descriptorType.equals(Constants.DRAGON7)) {
            ReadDescriptors.readDragon7Descriptors(sdfFile + ".dragon7", descriptorNames, descriptorValueMatrix);
        } else {
            throw new RuntimeException("Bad descriptor type: " + descriptorType);
        }
    }

    public static void readCommonDragonDescriptors(String dragonOutputFile, List<String> descriptorNames,
                                                   List<Descriptors> descriptorValueMatrix, boolean hasHeader) throws Exception {

        logger.debug("reading Dragon Descriptors");
        logger.debug(dragonOutputFile);
        File file = new File(dragonOutputFile);
        if (!file.exists() ) {
            throw new Exception("Could not read Dragon descriptors. (File Missing)\n");
        }else if(file.length() == 0){
            throw new Exception("Could not read Dragon descriptors. (File Length is 0)\n");
        }
        FileReader fin = new FileReader(file);
        BufferedReader br = new BufferedReader(fin);
        /* values for each molecule */
        List<String> descriptorValues;

        if (hasHeader) {
            br.readLine(); // junk line, should say "dragonX: Descriptors"
            br.readLine(); // contains some numbers
        }

        /* the descriptor names are on this line */
        String line = br.readLine();
        Scanner tok = new Scanner(line);
        while (tok.hasNext()) {
            String dname = tok.next();
            descriptorNames.add(dname);
        }
        tok.close();
        /* contains molecule name, which isn't a descriptor */
        descriptorNames.remove(1);
        descriptorNames.remove(0);

        /*
         * read in the descriptor values. If one of them is the word "Error" ,
         * quit this shit - means Dragon failed at descriptoring.
         */
        while ((line = br.readLine()) != null) {
            tok = new Scanner(line);
            descriptorValues = new ArrayList<>();
            descriptorValues.clear();
            while (tok.hasNext()) {
                String dvalue = tok.next();
                if (dvalue.equalsIgnoreCase("Error")) {
                    tok.close();
                    throw new Exception("Dragon descriptors invalid!");
                }
                descriptorValues.add(dvalue);
            }
            tok.close();

            Descriptors di = new Descriptors();
            /* contains molecule name, which isn't a descriptor */
            di.setCompoundName(descriptorValues.remove(1));
            di.setCompoundIndex(Integer.parseInt(descriptorValues.remove(0)));

            di.setDescriptorValues(Utility.stringListToDoubleList(descriptorValues));
            descriptorValueMatrix.add(di);
            descriptorValues.clear();
        }
        br.close();
    }

    public static void readDragonXDescriptors(String dragonOutputFile, List<String> descriptorNames,
                                              List<Descriptors> descriptorValueMatrix) throws Exception {
        readCommonDragonDescriptors(dragonOutputFile, descriptorNames, descriptorValueMatrix, true);
    }

    public static void readDragon7Descriptors(String dragonOutputFile, List<String> descriptorNames,
                                              List<Descriptors> descriptorValueMatrix) throws Exception {
        readCommonDragonDescriptors(dragonOutputFile, descriptorNames, descriptorValueMatrix, false);
    }

    public static void readMaccsDescriptors(String maccsOutputFile, List<String> descriptorNames,
                                            List<Descriptors> descriptorValueMatrix) throws Exception {
        // generate with "maccs.sh infile.sdf outfile.maccs"

        logger.debug("reading Maccs Descriptors");

        File file = new File(maccsOutputFile);
        if (!file.exists() || file.length() == 0) {
            throw new Exception("Could not read MACCS keys.\n");
        }
        FileReader fin = new FileReader(file);
        BufferedReader br = new BufferedReader(fin);
        /* first line is junk, it says "name,FP:MACCS." */
        String line = br.readLine();

        while ((line = br.readLine()) != null) {
            List<Double> descriptorValues = new ArrayList<>();
            Scanner tok = new Scanner(line);
            tok.useDelimiter(",");
            tok.next(); // skip compound identifier
            String tmp = tok.next();
            tok.close();
            tok = new Scanner(tmp);
            tok.useDelimiter(" ");
            int last = 0;
            int descriptor = 0;
            while (tok.hasNext()) {
                descriptor = Integer.parseInt(tok.next());
                for (int i = last; i < descriptor; i++) {
                    descriptorValues.add(0d);
                }
                descriptorValues.add(1d);
                last = descriptor + 1;
            }
            tok.close();
            for (int i = last; i < Constants.NUM_MACCS_KEYS; i++) {
                descriptorValues.add(0d);
            }
            Descriptors di = new Descriptors();
            di.setDescriptorValues(descriptorValues);
            descriptorValueMatrix.add(di);

        }
        br.close();
        for (int i = 0; i < Constants.NUM_MACCS_KEYS; i++) {
            descriptorNames.add((new Integer(i)).toString());
        }
    }

    public static void readMoe2DDescriptors(String moe2DOutputFile, List<String> descriptorNames,
                                            List<Descriptors> descriptorValueMatrix) throws Exception {
        logger.debug("reading Moe2D Descriptors");

        File file = new File(moe2DOutputFile);
        if (!file.exists() || file.length() == 0) {
            throw new Exception("Could not read MOE2D descriptors.\n");
        }
        FileReader fin = new FileReader(file);
        BufferedReader br = new BufferedReader(fin);
        /* contains descriptor names */
        String line = br.readLine();
        Scanner tok = new Scanner(line);
        tok.useDelimiter(",");
        /* first descriptor says "name"; we don't need that. */
        tok.next();
        while (tok.hasNext()) {
            descriptorNames.add(tok.next());
        }
        while ((line = br.readLine()) != null) {
            tok = new Scanner(line);
            tok.useDelimiter(",");
            if (tok.hasNext()) {
                /* first descriptor value is the name of the compound */
                tok.next();
            }
            List<Double> descriptorValues = new ArrayList<>();
            while (tok.hasNext()) {
                String val = tok.next();
                if (val.contains("NaN") || val.contains("e")) {
                    /*
                     * there's a divide-by-zero error for MOE2D sometimes.
                     * Results in NaN or "e+23" type numbers. only happens on
                     * a few descriptors, so it should be OK to just call it a
                     * 0 and move on.
                     */
                    val = "0";
                }
                descriptorValues.add(Double.parseDouble(val));
            }
            if (!descriptorValues.isEmpty()) {
                Descriptors di = new Descriptors();
                di.setDescriptorValues(descriptorValues);
                descriptorValueMatrix.add(di);
            }
            tok.close();
        }
        br.close();
    }

    public static void readIsidaDescriptors(String ISIDAOutputFile, List<String> descriptorNames,
                                            List<Descriptors> descriptorValueMatrix) throws Exception {
        logger.debug("reading ISIDA Descriptors");

        Path[] paths = getIsidaFilePaths(ISIDAOutputFile);
        Path headerFilePath = paths[0];
        Path datafilePath = paths[1];

        List<String> fragments = new ArrayList<>(); // list of each fragment string in the header file, e.g. "H-C"
        fragments.add(""); // XXX fence-value for [0] since fragments are 1-indexed
        appendIsidaFragments(fragments, headerFilePath);

        // XXX LinkedHashMap is important: need to keep this map's keys in order of insertion
        LinkedHashMap<String, SortedMap<Integer, Integer>> compoundNameToFragmentCounts = Maps.newLinkedHashMap();  // compound index => (fragment index => fragment count)
        appendIsidaCompounds(compoundNameToFragmentCounts, datafilePath);

        int compoundIndex = 1; // Descriptors.compoundIndex is 1-indexed
        if (descriptorValueMatrix == null) {
            descriptorValueMatrix = new ArrayList<>();
        }
        // XXX fragment names are 1-indexed in the .hdr file
        descriptorNames.addAll(fragments.subList(1, fragments.size()));
        for (String compoundName : compoundNameToFragmentCounts.keySet()) {
            SortedMap<Integer, Integer> fragmentCounts = compoundNameToFragmentCounts.get(compoundName);
            Descriptors d = new Descriptors();
            d.setCompoundIndex(compoundIndex++);
            d.setCompoundName(compoundName);
            List<Double> fragmentCountsForCompound = new ArrayList<>();
            // XXX fragments are 1-indexed (note loop starting point)
            // for each fragment index, add the corresponding value in fragmentCounts (or 0 if it's null)
            for (int i = 1; i < fragments.size(); i++) {
                fragmentCountsForCompound.add(MoreObjects.firstNonNull(fragmentCounts.get(i), 0).doubleValue());
            }
            d.setDescriptorValues(fragmentCountsForCompound);
            descriptorValueMatrix.add(d);
        }
    }

    // Read two ISIDA outputs and return a Descriptors list for each that unions the fragments from both ISIDA files
    public static void readJoinedIsidaDescriptors(String ISIDAOutputFile1, String ISIDAOutputFile2,
                                                  List<Descriptors> descriptorValueMatrix1, List<Descriptors> descriptorValueMatrix2) throws Exception {
        logger.debug("reading joined ISIDA Descriptors");

        Path[] paths1 = getIsidaFilePaths(ISIDAOutputFile1);
        Path[] paths2 = getIsidaFilePaths(ISIDAOutputFile2);

        // create set of all fragment strings in either header file
        TreeSet<String> fragmentStrings = new TreeSet<>();
        appendIsidaFragments(fragmentStrings, paths1[0]);
        appendIsidaFragments(fragmentStrings, paths2[0]);

        // populate the Descriptors list for each source, using the combined set of fragment strings
        readSingleJoinedIsidaFile(ISIDAOutputFile1, fragmentStrings, descriptorValueMatrix1);
        readSingleJoinedIsidaFile(ISIDAOutputFile2, fragmentStrings, descriptorValueMatrix2);
    }


    // populate the Descriptors list for this ISIDA file, using this set of fragments
    private static void readSingleJoinedIsidaFile(String ISIDAOutputFile, Set<String> fragmentStrings, List<Descriptors> descriptorValueMatrix){
        Path[] paths = getIsidaFilePaths(ISIDAOutputFile);
        Path headerFilePath = paths[0];
        Path dataFilePath = paths[1];

        // read header file into a map of fragment string => fragment index
        HashMap<String, Integer> fragmentStringToIndex = new HashMap<>();
        int index=1;    //fragments are 1-indexed in .svm
        try (BufferedReader reader = Files.newBufferedReader(headerFilePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = ISIDA_HEADER_REGEX.matcher(line);
                matcher.matches();
                fragmentStringToIndex.put(matcher.group(1), index++);
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read ISIDA header file", e);
        }

        // build a map of compound index => (fragment index => fragment count)
        LinkedHashMap<String, SortedMap<Integer, Integer>> compoundNameToFragmentCounts = Maps.newLinkedHashMap();
        appendIsidaCompounds(compoundNameToFragmentCounts, dataFilePath);

        // for each compound, add a Descriptors to the outpt Descriptors list
        int compoundIndex = 1; // Descriptors.compoundIndex is 1-indexed
        for (String compoundName : compoundNameToFragmentCounts.keySet()) {
            SortedMap<Integer, Integer> fragmentCounts = compoundNameToFragmentCounts.get(compoundName);

            List<Double> paddedFragmentCounts = new ArrayList<>();  //fragment counts with 0s for any non-present fragments

            // for each fragment, add the corresponding value in fragmentCounts (or 0 if it's null)
            Iterator<String> it = fragmentStrings.iterator();
            while (it.hasNext()){
                String fragmentString = it.next();
                Integer fragmentIndex = fragmentStringToIndex.get(fragmentString);
                Integer count = 0;
                if (fragmentIndex != null){ //check if the fragment string is in this string=>index map
                    count = fragmentCounts.get(fragmentIndex);
                }
                // fragment string might be present in the map but not in this compound, for a dataset
                paddedFragmentCounts.add(MoreObjects.firstNonNull(count, 0).doubleValue());
            }

            descriptorValueMatrix.add(new Descriptors(compoundIndex++, compoundName, paddedFragmentCounts));
        }
    }

    // return the Paths for the ISIDA header file (.hdr) and data file (.svm), in that order
    private static Path[] getIsidaFilePaths(String ISIDAOutputFile){
        Path rawFilePath = Paths.get(ISIDAOutputFile);
        // for filenames like "modeling.sdf.ISIDA.svm" or even "modeling.sdf.ISIDA.svm_0" (split files),
        // remove the ".svm..." extension (assumption is that the header file is "modeling.sdf.ISIDA.hdr")
        Matcher m = ISIDA_FILENAME_REGEX.matcher(rawFilePath.getFileName().toString());
        m.matches();

        Path dirPath = rawFilePath.getParent();
        Path headerFilePath = dirPath.resolve(m.group(1) + ".hdr");
        Path datafilePath;
        if (m.group(2) == null) {
            datafilePath = dirPath.resolve(m.group(1) + ".svm");
        } else {
            datafilePath = dirPath.resolve(m.group(1) + m.group(2));
        }
        return new Path[]{headerFilePath, datafilePath};
    }


    // Add the fragment strings from an ISIDA header file to a Collection<String>
    private static void appendIsidaFragments(Collection<String> fragments, Path headerFilePath){
        try (BufferedReader reader = Files.newBufferedReader(headerFilePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = ISIDA_HEADER_REGEX.matcher(line);
                matcher.matches();
                fragments.add(matcher.group(1));
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read ISIDA header file", e);
        }
    }

    // build a map of compound index => (fragment index => fragment count)
    private static void appendIsidaCompounds(LinkedHashMap<String, SortedMap<Integer, Integer>> compoundNameToFragmentCounts, Path datafilePath){
        try (BufferedReader reader = Files.newBufferedReader(datafilePath, StandardCharsets.UTF_8)) {
            // isida data (.svm) file structure: compound name, followed by <fragment number>:<fragment count> pairs,
            // where pairs are ordered by ascending fragment number
            // ... (snip)
            // 6 1:1 2:4 3:2 4:6 5:3 6:1 7:2 8:2 9:1 10:1 11:2 12:4 13:4 ...
            // 289 2:2 4:6 5:6 19:6 20:6 21:6 22:8 23:8 24:4 25:3 39:1 ...
            // 370 2:5 4:7 5:6 19:6 20:6 21:6 22:4 23:4 24:2 39:2 40:4 ...
            // ...
            String line;
            Splitter lineSplitter = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();
            Splitter pairSplitter = Splitter.on(':');
            while ((line = reader.readLine()) != null) {
                List<String> items = lineSplitter.splitToList(line);
                String compoundName = items.get(0);
                SortedMap<Integer, Integer> fragmentCounts = Maps.newTreeMap();
                for (String fragmentPair : items.subList(1, items.size())) {
                    List<String> values = pairSplitter.splitToList(fragmentPair);
                    fragmentCounts.put(Integer.parseInt(values.get(0)), Integer.parseInt(values.get(1)));
                }
                compoundNameToFragmentCounts.put(compoundName, fragmentCounts);
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read ISIDA data file", e);
        }
    }



    public static void readXDescriptors(String xFile, List<String> descriptorNames,
                                        List<Descriptors> descriptorValueMatrix) throws Exception {
        logger.debug("Trying to read uploaded descriptors");
        File file = new File(xFile);
        if (!file.exists() || file.length() == 0) {
            logger.error(xFile + ": xFile not found");
            throw new Exception("Could not read X file descriptors: " + xFile + "\n");
        }

        try {
            FileReader fin = new FileReader(file);
            BufferedReader br = new BufferedReader(fin);
            String line = br.readLine(); // header. ignored.
            line = br.readLine(); // contains descriptor names
            Scanner tok = new Scanner(line);
            tok.useDelimiter("\\s+");
            while (tok.hasNext()) {
                descriptorNames.add(tok.next());
            }
            tok.close();

            while ((line = br.readLine()) != null) {
                tok = new Scanner(line);
                tok.useDelimiter("\\s+");
                Descriptors di = new Descriptors();
                if (tok.hasNext()) {
                    di.setCompoundIndex(Integer.parseInt(tok.next())); // first value is the index of the compound
                }
                if (tok.hasNext()) {
                    di.setCompoundName(tok.next()); // second value is the name of the compound
                }
                List<Double> descriptorValues = new ArrayList<>();
                while (tok.hasNextDouble()) {
                    descriptorValues.add(tok.nextDouble());
                }
                if (!descriptorValues.isEmpty()) {
                    di.setDescriptorValues(descriptorValues);
                    descriptorValueMatrix.add(di);
                }
                tok.close();
            }
            br.close();
        } catch (FileNotFoundException e) {
            logger.error(file + ": File not found");
        }
    }
}
