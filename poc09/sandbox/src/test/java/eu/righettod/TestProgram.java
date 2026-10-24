package eu.righettod;

import org.junit.jupiter.api.Test;

import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class TestProgram {

    @Test
    public void testParse() throws  Exception{
        Program p = new Program();
        File pdfFile = new File("sample.pdf");
        int pageCount = p.parse(pdfFile);
        assertNotEquals(0, pageCount);
    }
}
