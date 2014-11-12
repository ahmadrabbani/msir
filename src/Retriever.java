import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.FuzzyTermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.payloads.AveragePayloadFunction;
import org.apache.lucene.search.payloads.PayloadFunction;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.search.similarities.*;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Debasis
 */

interface QueryConstructor {
    Query process() throws Exception;
}

class NGramQuery implements QueryConstructor {
    String text;
    Indexer indexer;
    
    public NGramQuery(String text, Indexer indexer) {
        this.text = text;
        this.indexer = indexer;
    }

    
    @Override
    public Query process() throws Exception {
        Analyzer analyzer = indexer.getAnalyzer();
        BooleanQuery query = new BooleanQuery();
        
        TokenStream ts = analyzer.tokenStream("query", text);
        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
        ts.reset();
        while (ts.incrementToken()) {
            String term = termAtt.toString().trim();
            if (MSIRDoc.isRomanScript(term)) {
                query.add(new TermQuery(new Term(MSIRDoc.FIELD_EN, termAtt.toString())), BooleanClause.Occur.SHOULD);
                query.add(new TermQuery(new Term(MSIRDoc.FIELD_TITLE_EN, termAtt.toString())), BooleanClause.Occur.SHOULD);
            }
            else {
                query.add(new TermQuery(new Term(MSIRDoc.FIELD_HN, termAtt.toString())), BooleanClause.Occur.SHOULD);
                query.add(new TermQuery(new Term(MSIRDoc.FIELD_TITLE_HN, termAtt.toString())), BooleanClause.Occur.SHOULD);
            }
        }
        ts.end();
        ts.close();
        return query;
    }
    
}

// Mixed script query
class MSQuery extends MSIRDoc implements QueryConstructor {
    String text;
    Word2VecQE w2vqe;
    boolean word2vecqexp;
    
    public MSQuery(String text, Indexer indexer) {
        super(indexer);        
        this.text = text;
        
        try {
            word2vecqexp = Boolean.parseBoolean(indexer.prop.getProperty("qexp.wvec", "false"));
            if (word2vecqexp)
                w2vqe = new Word2VecQE(indexer.prop);        
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    @Override
    public Query process() throws Exception {
        Analyzer analyzer = indexer.getAnalyzer();
        //Analyzer analyzer = new PerFieldAnalyzerWrapper(
        //        new ShingleAnalyzerWrapper(new WhitespaceAnalyzer(Version.LUCENE_46),
        //        Indexer.ShingleSize, Indexer.ShingleSize, "#", false, false));
                
        List<String> words = new ArrayList<>();
        BooleanQuery query = new BooleanQuery();
        Query enquery = null;
        Query hnquery = null;
                
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            words.add(token);
        }
        
        float simThresh = Float.parseFloat(indexer.prop.getProperty("qexp.sim.thresh", "0.8"));
        StringBuffer enText = new StringBuffer();
        StringBuffer hnText = new StringBuffer();
        
        //boolean mosesTrans = Boolean.parseBoolean(indexer.prop.getProperty("query.transliterated", "false"));
        // Add the transliterating equivalents
        for (String qword : words) {
            qword = normalize(qword);
            //if (!mosesTrans)
            qword = indexer.getNormMode()==Indexer.NORM_MODE_SYNSET?
                        normalizeWithSynSet(qword) : normalizeWithoutSynSet(qword);
            
            // note that qword is a space separated list of words
            String[] qwordunits = qword.split("\\s+");
            
            for (String qwordunit : qwordunits) {
                
                if (!isRomanScript(qwordunit))
                    hnText.append(qwordunit).append(" ");
                else    
                    enText.append(qwordunit).append(" ");
                
                if (!word2vecqexp)
                    continue;
                if (qwordunit.length()==0)
                    continue;
                //if (words.size() > 3)
                //    continue;
                //if (!isRomanScript(qwordunit))
                //    continue;

                // Do the word2vec based query expansion
                Wordvec[] expandedWords = w2vqe.getNearestNeighbors(qwordunit);
                if (expandedWords == null) {
                    System.err.println("|" + qwordunit + "| not found in dict!");
                    continue;
                }
                System.out.println("Expanding query " + text);
                
                String fieldName = null;
                TermQuery tq = null;
                for (Wordvec expwordvec : expandedWords) {
                    String expword = expwordvec.word;
                    if (expwordvec.querySim < simThresh)
                        break;
                    expword = normalize(expword);
                    fieldName = isRomanScript(expword)? MSIRDoc.FIELD_TITLE_EN : FIELD_TITLE_HN;
                    tq = new TermQuery(new Term(fieldName, expword));
                    //tq.setBoost(expwordvec.querySim);
                    query.add(tq, BooleanClause.Occur.SHOULD);
                    fieldName = isRomanScript(expword)? MSIRDoc.FIELD_EN : FIELD_HN;
                    tq = new TermQuery(new Term(fieldName, expword));
                    //tq.setBoost(expwordvec.querySim);
                    query.add(tq, BooleanClause.Occur.SHOULD);
                }            
            }            
        }
        
        if (enText.length() > 0) enText.deleteCharAt(enText.length()-1);
        if (hnText.length() > 0) hnText.deleteCharAt(hnText.length()-1);
        
        // Multiphrases (with ShingleAnalyzer)
        // English field
        QueryParser qp = null;
        qp = new QueryParser(Version.LUCENE_46, MSIRDoc.FIELD_TITLE_EN, analyzer);
        String enTextStr = enText.toString();
        if (enTextStr.length() > 0)
            enquery = qp.parse("\"" + enTextStr + "\"");
        
        if (enquery != null) {
            //enquery.setBoost(5);
            query.add(enquery, BooleanClause.Occur.SHOULD);
        }
        
        qp = new QueryParser(Version.LUCENE_46, MSIRDoc.FIELD_EN, analyzer);
        enTextStr = enText.toString();
        if (enTextStr.length() > 0)
            enquery = qp.parse("\"" + enTextStr + "\"");
        
        if (enquery != null) {
            //enquery.setBoost(5);
            query.add(enquery, BooleanClause.Occur.SHOULD);
        }
        
        // Hindi field
        qp = new QueryParser(Version.LUCENE_46, MSIRDoc.FIELD_TITLE_HN, analyzer);
        String hnTextStr = hnText.toString();
        if (hnTextStr.length() > 0)
            hnquery = qp.parse("\"" + hnTextStr + "\"");

        if (hnquery != null) {
            //hnquery.setBoost(5);
            query.add(hnquery, BooleanClause.Occur.SHOULD);
        }

        qp = new QueryParser(Version.LUCENE_46, MSIRDoc.FIELD_HN, analyzer);
        hnTextStr = hnText.toString();
        if (hnTextStr.length() > 0)
            hnquery = qp.parse("\"" + hnTextStr + "\"");
        
        if (hnquery != null) {
            //hnquery.setBoost(5);
            query.add(hnquery, BooleanClause.Occur.SHOULD);
        }
        
        String[] enwords = enTextStr.split("\\s+");
        TermQuery tq = null;
        FuzzyQuery fq = null;
        boolean fuzzyTermOption = Boolean.parseBoolean(indexer.prop.getProperty("fuzzyq", "false"));
        
        for (String enword : enwords) {
            if (fuzzyTermOption && enword.length() > 3) { 
                fq = new FuzzyQuery(new Term(MSIRDoc.FIELD_TITLE_EN, enword), 2);
                fq.setBoost(0.5f);
                query.add(fq, BooleanClause.Occur.SHOULD);
                fq = new FuzzyQuery(new Term(MSIRDoc.FIELD_EN, enword), 2);
                query.add(fq, BooleanClause.Occur.SHOULD);
                fq.setBoost(0.5f);
            }
            tq = new TermQuery(new Term(MSIRDoc.FIELD_TITLE_EN, enword));
            //tq.setBoost(1);
            query.add(tq, BooleanClause.Occur.SHOULD);
            tq = new TermQuery(new Term(MSIRDoc.FIELD_EN, enword));
            //tq.setBoost(1);
            query.add(tq, BooleanClause.Occur.SHOULD);
        }

        String[] hnwords = hnTextStr.split("\\s+");
        for (String hnword : hnwords) {
            /*
            if (fuzzyTermOption && hnword.length() > 5) { 
                fq = new FuzzyQuery(new Term(MSIRDoc.FIELD_TITLE_HN, hnword), 2);
                fq.setBoost(0.5f);
                query.add(fq, BooleanClause.Occur.SHOULD);
                fq = new FuzzyQuery(new Term(MSIRDoc.FIELD_HN, hnword), 2);
                fq.setBoost(0.25f);
                query.add(fq, BooleanClause.Occur.SHOULD);
            }
            */
            tq = new TermQuery(new Term(MSIRDoc.FIELD_TITLE_HN, hnword));
            //tq.setBoost(2);
            query.add(tq, BooleanClause.Occur.SHOULD);
            tq = new TermQuery(new Term(MSIRDoc.FIELD_HN, hnword));
            //tq.setBoost(1);
            query.add(tq, BooleanClause.Occur.SHOULD);
        }
        
        return query;
    }
}

public class Retriever {
    
    IndexSearcher searcher;
    Properties prop;
    int queryStartId;
    String rfile;
    Indexer indexer;
    
    public Retriever(String propFile) throws Exception {
        indexer = new Indexer(propFile);
        
        String index_dir = null;
        prop = new Properties();
        prop.load(new FileReader(propFile));
        index_dir = prop.getProperty("index");
        
        try {
            File indexDir = new File(index_dir);
            IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir));

            searcher = new IndexSearcher(reader);

            Similarity[] sims = {
                new LMJelinekMercerSimilarity(0.7f),
                //new BM25Similarity(1.25f, 1.0f),
                //new IBSimilarity(new DistributionLL(), new LambdaDF(), new NormalizationH3()),
                //new DFRSimilarity(new BasicModelP(), new AfterEffect.NoAfterEffect(), new NormalizationZ()),
                //new DefaultSimilarity()
            };
            
            Similarity msim = new MultiSimilarity(sims);
            searcher.setSimilarity(msim);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }    
        
        rfile = prop.getProperty("results_file");
    }

    Query constructQuery(String line) {
        String[] tokens = line.split("\t");
        
        if (queryStartId == 0)
            queryStartId = Integer.parseInt(tokens[0]);

        String indexingUnit = indexer.prop.getProperty("indexing_unit", "words");        
        QueryConstructor qc = !indexingUnit.equals("ngrams")?
            new MSQuery(tokens[1], indexer) : new NGramQuery(tokens[1], indexer);

        //QueryConstructor qc = new MSQuery(tokens[1], indexer);
        Query q = null;
        try {
            q = qc.process();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
        return q;
    }
    
    public List<Query> constructQueries() throws Exception {
        // Create an instance of the PGN parser
        String qryFile = prop.getProperty("query_file");
        List<Query> queryList = new LinkedList<Query>();
        FileReader fr = new FileReader(qryFile);
        BufferedReader br = new BufferedReader(fr);
        String line;
        while ((line = br.readLine()) != null) {            
            queryList.add(constructQuery(line));
        }
        return queryList;
    }
    
    public void retrieveAll() throws Exception {
        int numWanted = Integer.parseInt(prop.getProperty("num_wanted", "1000"));
        ScoreDoc[] hits = null;
        TopDocs topDocs = null;
        FileWriter fw = new FileWriter(rfile);
        List<Query> qlist = constructQueries();
        int qid = queryStartId;
        String runName = prop.getProperty("runname", "baseline");
        
        for (Query query : qlist) {
            TopScoreDocCollector collector = TopScoreDocCollector.create(numWanted, true);
            System.out.println(query);
            
            searcher.search(query, collector);
            topDocs = collector.topDocs();
            hits = topDocs.scoreDocs;

            StringBuffer buff = new StringBuffer();
            
            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                buff.append(qid).append("\tQ0\t").
                    append(d.get(MSIRDoc.FIELD_DOC_NAME)).append("\t").
                    append(i+1).append("\t").
                    append(hits[i].score).append("\t").
                    append(runName).append("\n");
            }
            if (buff.length() > 0)
                fw.write(buff.toString());
            
            qid++;
        }
        fw.flush();
        fw.close();
    }
    
    void evaluate() throws Exception {
        boolean toEval = Boolean.parseBoolean(prop.getProperty("toeval", "false"));
        if (!toEval)
            return;
        String evalProgram = prop.getProperty("evalutil");
        String qrelfile = prop.getProperty("qrels");
        ProcessBuilder pb = new ProcessBuilder("perl", evalProgram, qrelfile, rfile, "0");
        Process proc = pb.start();
        proc.waitFor();
    }
    
    public static void main(String[] args) {
        String propFile = "init.properties";
        if (args.length > 0) {
            propFile = args[0];
        }
        
        try {
            Retriever retriever = new Retriever(propFile);
            retriever.retrieveAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
