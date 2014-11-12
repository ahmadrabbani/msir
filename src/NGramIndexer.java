
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.util.Version;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author dganguly
 */

class NGramAnalyzer extends Analyzer {

    int n;
    
    public NGramAnalyzer(int n) {
        this.n = n;
    }
  
    @Override
    protected TokenStreamComponents createComponents(String string, Reader reader) {
		TokenStream result = null;

		Tokenizer source = new NGramTokenizer(Version.LUCENE_46, reader, n, n);
        result = source;

		return new TokenStreamComponents(source, result);
    }
    
}

class NGramIndexer extends Indexer {

    public NGramIndexer(String propFile) throws Exception {
        super(propFile);
        
        int n = Integer.parseInt(prop.getProperty("ngrams", "3"));
        analyzer = new NGramAnalyzer(n);        
    }    
}

class IndexerFactory {
    static Indexer createIndexer(String propFile) throws Exception {
        Indexer indexer = null;
        Properties prop = new Properties();
        prop.load(new FileReader(propFile));
        String indexingUnit = prop.getProperty("indexing_unit", "words");
        if (indexingUnit.equals("ngrams"))
            indexer = new NGramIndexer(propFile);
        else
            indexer = new Indexer(propFile);
        return indexer;
    }
}
