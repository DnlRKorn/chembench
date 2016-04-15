package edu.unc.ceccr.chembench.workflows.descriptors;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import edu.unc.ceccr.chembench.global.Constants;
import edu.unc.ceccr.chembench.persistence.Descriptors;
import edu.unc.ceccr.chembench.persistence.Predictor;
import edu.unc.ceccr.chembench.utilities.RunExternalProgram;
import edu.unc.ceccr.chembench.utilities.Utility;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadDescriptors {
    // Read in the output of a descriptor generation program
    // (molconnZ, dragon, etc.)
    // Create a Descriptors object for each compound.
    // puts results into descriptorNames and descriptorValueMatrix.

    private static final Logger logger = Logger.getLogger(ReadDescriptors.class);
    private static final Pattern ISIDA_HEADER_REGEX = Pattern.compile("\\s*\\d+\\.\\s*(.+)");

    public static String[] readDescriptorNamesFromX(String xFile, String workingDir) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(workingDir + xFile));
        br.readLine(); // numCompounds, numDescriptors;
        String[] descs = br.readLine().split("\\s+");
        br.close();
        return descs;
    }

    public static void convertMzToX(String molconnZOutputFile, String workingDir) throws Exception {
        String cmd = "python " + Constants.CECCR_BASE_PATH + Constants.SCRIPTS_PATH + "mzToX.py " +
                molconnZOutputFile + " " + molconnZOutputFile + ".x";
        RunExternalProgram.runCommandAndLogOutput(cmd, workingDir, "mzToX.py");

        // Any errors from MolconnZ processing will be in the log files. Read
        // 'em.
    }

    public static void convertCDKToX(String cdkOutputFile, String workingDir) throws Exception {
        String cmd = "python " + Constants.CECCR_BASE_PATH + Constants.SCRIPTS_PATH + "cdkToX.py " + cdkOutputFile +
                " " + cdkOutputFile + ".x";
        RunExternalProgram.runCommandAndLogOutput(cmd, workingDir, "cdkToX.py");

        // Any errors from MolconnZ processing will be in the log files. Read
        // 'em.
    }

    public static void readDescriptors(Predictor predictor, String sdfFile, List<String> descriptorNames,
                                       List<Descriptors> descriptorValueMatrix) throws Exception {
        if (predictor.getDescriptorGeneration().equals(Constants.MOLCONNZ)) {
            ReadDescriptors.readMolconnZDescriptors(sdfFile + ".molconnz", descriptorNames, descriptorValueMatrix);
        } else if (predictor.getDescriptorGeneration().equals(Constants.CDK)) {
            ReadDescriptors.readXDescriptors(sdfFile + ".cdk.x", descriptorNames, descriptorValueMatrix);
        } else if (predictor.getDescriptorGeneration().equals(Constants.DRAGONH)) {
            ReadDescriptors.readDragonDescriptors(sdfFile + ".dragonH", descriptorNames, descriptorValueMatrix);
        } else if (predictor.getDescriptorGeneration().equals(Constants.DRAGONNOH)) {
            ReadDescriptors.readDragonDescriptors(sdfFile + ".dragonNoH", descriptorNames, descriptorValueMatrix);
        } else if (predictor.getDescriptorGeneration().equals(Constants.MOE2D)) {
            ReadDescriptors.readMoe2DDescriptors(sdfFile + ".moe2D", descriptorNames, descriptorValueMatrix);
        } else if (predictor.getDescriptorGeneration().equals(Constants.MACCS)) {
            ReadDescriptors.readMaccsDescriptors(sdfFile + ".maccs", descriptorNames, descriptorValueMatrix);
        } else if (predictor.getDescriptorGeneration().equals(Constants.ISIDA)) {
            ReadDescriptors.readISIDADescriptors(sdfFile + ".ISIDA", descriptorNames, descriptorValueMatrix);
        } else if (predictor.getDescriptorGeneration().equals(Constants.UPLOADED)) {
            ReadDescriptors.readXDescriptors(sdfFile + ".x", descriptorNames, descriptorValueMatrix);
        } else {
            throw new RuntimeException("Bad descriptor type: " + predictor.getDescriptorGeneration());
        }
    }

    public static void readMolconnZDescriptors(String molconnZOutputFile, List<String> descriptorNames,
                                               List<Descriptors> descriptorValueMatrix) throws Exception {

        logger.debug("reading MolconnZ Descriptors");

        File file = new File(molconnZOutputFile);
        if (!file.exists() || file.length() == 0) {
            throw new Exception("Could not read MolconnZ descriptors from file:" + " " + molconnZOutputFile + "\n");
        }
        FileReader fin = new FileReader(file);

        String temp;
        Scanner src = new Scanner(fin);
        // values for each molecule
        List<String> descriptorValues = Lists.newArrayList();

        boolean readingDescriptorNames = true;
        while (src.hasNext()) {
            // sometimes MolconnZ spits out nonsensical crap like ���C
            // along with
            // a descriptor value. Filter that out.
            temp = src.next();
            if (temp.matches("not_available")) {
                // molconnz will spit out a not_available if it gets a bad
                // molecule.
                descriptorValues.clear();
            }
            if (temp.matches("[\\p{Graph}]+")) {

                if (temp.matches("[0-9&&[^a-zA-Z]]+") && readingDescriptorNames) {
                    // The first occurrence of a number indicates we're no
                    // longer reading descriptor names.
                    // "1" will indicate the first molecule, no matter what
                    // the SDF
                    // had as molecule numbers.
                    readingDescriptorNames = false;
                }

                if (readingDescriptorNames) {
                    descriptorNames.add(temp);
                } else {
                    if (descriptorValues.size() == descriptorNames.size()) {
                        // done reading values for this molecule.

                        String formula = descriptorValues.get(Constants.MOLCONNZ_FORMULA_POS);
                        // formula should look something like C(12)H(22)O(11)
                        if (!formula.contains("(")) {
                            // the formula for the molecule isn't a formula
                            // usually indicates missing descriptors
                            // on the previous molecule
                            throw new Exception("MolconnZ error: Molecule " + descriptorValues
                                    .get(Constants.MOLCONNZ_COMPOUND_NAME_POS) + " has formula " + descriptorValues
                                    .get(Constants.MOLCONNZ_FORMULA_POS));
                        }
                        /* contains molecule name, which isn't a descriptor */
                        descriptorValues.remove(Constants.MOLCONNZ_FORMULA_POS);
                        /* contains molecule name, which isn't a descriptor */
                        descriptorValues.remove(Constants.MOLCONNZ_COMPOUND_NAME_POS);
                        /* contains molecule ID, which isn't a descriptor */
                        descriptorValues.remove(0);
                        Descriptors di = new Descriptors();
                        di.setDescriptorValues(Utility.StringListToString(descriptorValues));
                        descriptorValueMatrix.add(di);
                        descriptorValues.clear();
                    }

                    /*
                     * a couple more special cases for when MolconnZ decides
                     * to go crazy
                     */
                    if (temp.equals("inf")) {
                        temp = "9999";
                    } else if (temp.equals("-inf")) {
                        temp = "-9999";
                    } else if (temp.equals("not_available")) {
                        /*
                         * quit this shit - means MolconnZ failed at
                         * descriptoring and all values past this point will
                         * be offset.
                         */
                        throw new Exception("MolconnZ descriptors invalid!");
                    }
                    descriptorValues.add(temp);
                }
            }
        }
        /* add the last molecule's descriptors */
        /* contains molecule name, which isn't a descriptor */
        descriptorValues.remove(Constants.MOLCONNZ_FORMULA_POS);
        descriptorNames.remove(Constants.MOLCONNZ_FORMULA_POS);
        /* contains molecule name, which isn't a descriptor */
        descriptorValues.remove(Constants.MOLCONNZ_COMPOUND_NAME_POS);
        descriptorNames.remove(Constants.MOLCONNZ_COMPOUND_NAME_POS);
        /* contains molecule ID, which isn't a descriptor */
        descriptorValues.remove(0);
        descriptorNames.remove(0);
        Descriptors di = new Descriptors();
        di.setDescriptorValues(Utility.StringListToString(descriptorValues));
        descriptorValueMatrix.add(di);

        src.close();
        fin.close();
    }

    public static void readDragonDescriptors(String dragonOutputFile, List<String> descriptorNames,
                                             List<Descriptors> descriptorValueMatrix) throws Exception {

        logger.debug("reading Dragon Descriptors");

        File file = new File(dragonOutputFile);
        if (!file.exists() || file.length() == 0) {
            throw new Exception("Could not read Dragon descriptors.\n");
        }
        FileReader fin = new FileReader(file);
        BufferedReader br = new BufferedReader(fin);
        /* values for each molecule */
        List<String> descriptorValues;
        /* junk line, should say "dragonX: Descriptors" */
        String line = br.readLine();

        /* contains some numbers */
        line = br.readLine();
        Scanner tok = new Scanner(line);
        // int num_molecules = Integer.parseInt(tok.next());

        /* just says "2" all the time, no idea what that means, so skip that */
        tok.next();
        // int num_descriptors = Integer.parseInt(tok.next());

        /* the descriptor names are on this line */
        line = br.readLine();
        tok.close();
        tok = new Scanner(line);
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
            descriptorValues = Lists.newArrayList();
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

            di.setDescriptorValues(Utility.StringListToString(descriptorValues));
            descriptorValueMatrix.add(di);
            descriptorValues.clear();
        }
        br.close();
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
            String descriptorString = new String("");
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
                    descriptorString += "0 ";
                }
                descriptorString += "1 ";
                last = descriptor + 1;
            }
            tok.close();
            for (int i = last; i < Constants.NUM_MACCS_KEYS; i++) {
                descriptorString += "0 ";
            }
            Descriptors di = new Descriptors();
            di.setDescriptorValues(descriptorString);
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
            String descriptorString = new String("");
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
                descriptorString += val + " ";
            }
            if (!descriptorString.equalsIgnoreCase("")) {
                Descriptors di = new Descriptors();
                di.setDescriptorValues(descriptorString);
                descriptorValueMatrix.add(di);
            }
            tok.close();
        }
        br.close();
    }

    public static void readISIDADescriptors(String ISIDAOutputFile, List<String> descriptorNames,
                                            List<Descriptors> descriptorValueMatrix) throws Exception {
        logger.debug("reading ISIDA Descriptors");
        List<String> fragments = Lists.newArrayList();
        fragments.add(""); // XXX fence-value for [0] since fragments are 1-indexed
        try (BufferedReader reader = Files
                .newBufferedReader(Paths.get(ISIDAOutputFile + ".hdr"), StandardCharsets.UTF_8)) {
            String line;
            // isida header (.hdr) file structure: fragment number -> descriptor name,
            // where numbering starts from 1
            //    1.         Cl
            // ... (snip)
            //   24.         H-C
            //   25.         H-C*C-H
            //   26.         (Cl-C),(Cl-C*C),(Cl-C*C),(Cl-C*C*C),(Cl-C*C*C),(Cl-C*C-H),(Cl-C*C-N),xCl
            // ...
            while ((line = reader.readLine()) != null) {
                Matcher matcher = ISIDA_HEADER_REGEX.matcher(line);
                matcher.matches();
                fragments.add(matcher.group(1));
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read ISIDA header file", e);
        }

        // XXX LinkedHashMap is important: need to keep this map's keys in order of insertion
        LinkedHashMap<String, SortedMap<Integer, Integer>> compoundNameToFragmentCounts = Maps.newLinkedHashMap();
        try (BufferedReader reader = Files
                .newBufferedReader(Paths.get(ISIDAOutputFile + ".svm"), StandardCharsets.UTF_8)) {
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

        int compoundIndex = 1; // Descriptors.compoundIndex is 1-indexed
        if (descriptorValueMatrix == null) {
            descriptorValueMatrix = Lists.newArrayList();
        }
        // XXX fragment names are 1-indexed in the .hdr file
        descriptorNames = fragments.subList(1, fragments.size());
        Joiner joiner = Joiner.on(' ');
        for (String compoundName : compoundNameToFragmentCounts.keySet()) {
            SortedMap<Integer, Integer> fragmentCounts = compoundNameToFragmentCounts.get(compoundName);
            Descriptors d = new Descriptors();
            d.setCompoundIndex(compoundIndex++);
            d.setCompoundName(compoundName);
            List<Integer> fragmentCountsForCompound = Lists.newArrayList();
            // XXX fragments are 1-indexed (note loop starting point)
            for (int i = 1; i < fragments.size(); i++) {
                fragmentCountsForCompound.add(MoreObjects.firstNonNull(fragmentCounts.get(i), 0));
            }
            d.setDescriptorValues(joiner.join(fragmentCountsForCompound));
            descriptorValueMatrix.add(d);
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
                String descriptorString = new String("");
                while (tok.hasNext()) {
                    descriptorString += tok.next() + " ";
                }
                if (!descriptorString.equalsIgnoreCase("")) {
                    di.setDescriptorValues(descriptorString);
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
