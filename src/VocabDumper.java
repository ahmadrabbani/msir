
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author dganguly
 */
public class VocabDumper {
    
    File indexDir;
    
    VocabDumper(String propFile) throws Exception {
        Properties prop = new Properties();
        prop.load(new FileReader(propFile));
        indexDir = new File(prop.getProperty("index"));        
    }
    
    void printWords() throws IOException {
        IndexReader indexReader = DirectoryReader.open(FSDirectory.open(indexDir));
        Fields fields = MultiFields.getFields(indexReader);
        
        String[] fieldNames = { MSIRDoc.FIELD_TITLE_EN, MSIRDoc.FIELD_EN,
                                MSIRDoc.FIELD_TITLE_HN, MSIRDoc.FIELD_HN };
        
        for (String fieldName : fieldNames) {
            Terms terms = fields.terms(fieldName);
            TermsEnum iterator = terms.iterator(null);
            BytesRef byteRef = null;
            while((byteRef = iterator.next()) != null) {
                String term = new String(byteRef.bytes, byteRef.offset, byteRef.length);
                if (term.indexOf('#') == -1)
                    System.out.println(term);
            }
        }
    }
    
    public static void main(String[] args) {
        String propFile;
        if (args.length < 1)
            propFile = "init.properties";        
        else
            propFile = args[0];
        try {
            VocabDumper dumper = new VocabDumper(propFile);
            dumper.printWords();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
