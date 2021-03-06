import edu.unc.ceccr.chembench.workflows.descriptors.*;
import junitx.framework.ComparisonFailure;
import junitx.framework.FileAssert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class GenerateDescriptorsTest extends BaseTest {
    private static final String BASE_FILE_NAME = "hdac59";
    private static final String SDF_FILE_NAME = BASE_FILE_NAME + ".sdf";
    private static final Path resourcesDirPath = Paths.get("src", "test", "resources");
    private static Path sdfFilePath;
    private static Path tempDirPath;
    private static Path tempDescriptorsDirPath;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUpClass() throws IOException {
        tempDirPath = Files.createTempDirectory(GenerateDescriptorsTest.class.getName());
        tempDescriptorsDirPath = tempDirPath.resolve("Descriptors");
        sdfFilePath = Files.copy(resourcesDirPath.resolve(SDF_FILE_NAME), tempDirPath.resolve(SDF_FILE_NAME));
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        Files.walkFileTree(tempDirPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Test
    public void generateCdkDescriptors() throws Exception {
        DescriptorCDK descriptorCDK = new DescriptorCDK();
        descriptorCDK.generateDescriptors(sdfFilePath.toString(), descriptorCDK.getFileErrorOut());
        FileAssert.assertEquals(tempDescriptorsDirPath.resolve(descriptorCDK.getFileErrorOut()).toFile(),
                resourcesDirPath.resolve(BASE_FILE_NAME + descriptorCDK.getFileEnding()).toFile());
    }

    @Test
    public void generateIsidaDescriptorsWithAndWithoutHeader() throws Exception {
        DescriptorIsida descriptorIsida = new DescriptorIsida();
        descriptorIsida.generateDescriptors(sdfFilePath.toString(), sdfFilePath.toString());
        FileAssert.assertEquals(tempDescriptorsDirPath.resolve(descriptorIsida.getFileHdrEnding()).toFile(),
                resourcesDirPath.resolve(BASE_FILE_NAME + descriptorIsida.getFileHdrEnding()).toFile());
        FileAssert.assertEquals(tempDescriptorsDirPath.resolve(descriptorIsida.getFileSvmEnding()).toFile(),
                resourcesDirPath.resolve(BASE_FILE_NAME + descriptorIsida.getFileSvmEnding()).toFile());

        String otherHeader = "skin-sensitization.isida.hdr";
        Path otherHeaderPath = Files.copy(resourcesDirPath.resolve(otherHeader), tempDirPath.resolve(otherHeader));
        descriptorIsida.generateIsidaDescriptorsWithHeader(sdfFilePath.toString(), "isida-with-header",
                otherHeaderPath.toString());
        FileAssert.assertEquals(tempDescriptorsDirPath.resolve("isida-with-header.svm").toFile(),
                resourcesDirPath.resolve(BASE_FILE_NAME + "-against-skin-sensitization.isida.svm").toFile());

        // the output with the external header should not be the same as the output without it
        thrown.expect(ComparisonFailure.class);
        FileAssert.assertEquals(tempDescriptorsDirPath.resolve("isida-with-header.svm").toFile(),
                tempDescriptorsDirPath.resolve("isida.svm").toFile());
    }
}
