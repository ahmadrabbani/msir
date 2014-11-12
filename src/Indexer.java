import java.util.*;
import java.io.*;
import java.util.Map.Entry;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import sun.security.x509.IssuerAlternativeNameExtension;
	
	
class MSIRDoc {
	File docFile;
    Indexer indexer;
    String rawText;
    
	ArrayList<String[]> enwords;
	ArrayList<String[]> hnwords;
    ArrayList<String[]> enTitle;
    ArrayList<String[]> hnTitle;
    
    StringBuffer txtEnTitle;
    StringBuffer txtHnTitle;    
    StringBuffer txtEn;
    StringBuffer txtHn;
    
    static final String FIELD_DOC_NAME = "name";
    static final String FIELD_DOC_RAW_TEXT = "raw";
    static final String FIELD_TITLE_EN = "en_title";
    static final String FIELD_EN = "en_content";
    static final String FIELD_HN = "hn_content";
    static final String FIELD_TITLE_HN = "hn_title";
    
    static final String Delims = " \t,;-(){}[]\\#!%*+0123456789?=|':~/\"";
    static final int MinHnWordLen = 2;

    public MSIRDoc(Indexer indexer) {
        this.indexer = indexer;
        txtEnTitle = new StringBuffer();
        txtHnTitle = new StringBuffer();    
        txtEn = new StringBuffer();
        txtHn = new StringBuffer();
    }
    
	MSIRDoc(File docFile, Indexer indexer) {
		this.docFile = docFile;
        this.indexer = indexer;
        
		enwords = new ArrayList<String[]>();
		hnwords = new ArrayList<String[]>();
        enTitle = new ArrayList<String[]>();
        hnTitle = new ArrayList<String[]>();
        
        txtEnTitle = new StringBuffer();
        txtHnTitle = new StringBuffer();    
        txtEn = new StringBuffer();
        txtHn = new StringBuffer();        
	}

    String getEnTxt() { return txtEn.toString(); }
    String getHnTxt() { return txtHn.toString(); }
    
    void addWords(StringBuffer en_content, StringBuffer hn_content,
            String normalizedWords) {
        
        String[] terms = normalizedWords.split("\\s+");
        for (String term : terms) {
            if (isRomanScript(term)) {
                en_content.append(term).append(" ");
            }
            else {
                hn_content.append(term).append(" ");
            }
        }
        
        /*
        if (normalizedWord.equals(word))
            return;
        
        if (isRomanScript(word)) {
            en_content.append(word).append(" ");
        }
        else {
            hn_content.append(word).append(" ");
        }
        */
    }

    String normalizeTitle(String title) {
        StringBuffer newTitle = new StringBuffer();
        StringTokenizer st = new StringTokenizer(title, Delims);
        while (st.hasMoreTokens()) {
            String word = st.nextToken();
            word = normalize(word);
            newTitle.append(word).append(" ");
        }
        return newTitle.toString();
    }
        
    public void normalizeContent(ArrayList<String[]> enwords, ArrayList<String[]> hnwords,
            StringBuffer en_content, StringBuffer hn_content) {
        
        int normMode = indexer.getNormMode();
        
        ArrayList<String[]> allWords = new ArrayList<>(enwords);
        allWords.addAll(hnwords);
        
        for (String[] words : allWords) {
            for (String word : words) {
                
                String normalizedWords = normMode == Indexer.NORM_MODE_SYNSET? normalizeWithSynSet(word) :
                        normalizeWithoutSynSet(word);
                addWords(en_content, hn_content, normalizedWords);
                
            }
            if (en_content.length() > 0 && en_content.charAt(en_content.length()-1) != '\n')
                en_content.append("\n");
            if (hn_content.length() > 0 && hn_content.charAt(hn_content.length()-1) != '\n')
                hn_content.append("\n");
        }
    }
    
    String lengthThresholdFilter(String word) {
        if (!isRomanScript(word))
            return word;
        
        String lengthThresh = indexer.prop.getProperty("length_threshold");
        if (lengthThresh == null)
            return word;
        int val = Integer.parseInt(lengthThresh);
        return (word.length() >= val)? word : "";
    }
    
    String syllableNormFilter(String word) {
        boolean toapply = Boolean.parseBoolean(indexer.prop.getProperty("syllablenorm", "false"));
        if (!toapply)
            return word;
        
        if (!isRomanScript(word))
            return word;

        return TranslitNormalizer.normalize(word);
    }
    
    String normalizeWithoutSynSet(String word) {
        HashSet<String> expandedWords;
        expandedWords = new HashSet<>();
        
        if (!isRomanScript(word))
            expandedWords.add(word);
        
        HashMap<String, TreeSet<String>> translitMap = indexer.translitMap.getMap();
        String norm = word;
        TreeSet<String> wlist = translitMap.get(word);
        if (wlist != null) {
            norm = wlist.first();
            expandedWords.add(syllableNormFilter(norm));
        }
        else {
            // handle OOV words by applying simple rules, e.g. aa->a
            expandedWords.add(syllableNormFilter(norm));
            //System.out.println(word);
        }
        
        StringBuffer buff = new StringBuffer();
        for (String expandedWord : expandedWords)
            buff.append(expandedWord).append(" ");
        
        return buff.toString();
    }
    
    public void addAlignments() {
        HashMap<String, TreeSet<String>> translitMap = indexer.translitMap.getMap();
        if (enwords.size() != hnwords.size())
            return;
        
		Iterator<String[]> eniter = enwords.iterator();
		Iterator<String[]> hniter = hnwords.iterator();
        StringBuffer buff = new StringBuffer();

		while (eniter.hasNext() && hniter.hasNext()) {
			String[] enwords = eniter.next();
			String[] hnwords = hniter.next();
			int min = Math.min(enwords.length, hnwords.length);
			int max = enwords.length==min? hnwords.length : enwords.length;
            if (min != max)
                return;
            
			for (int i = 0; i < min; i++) {
                if (isRomanScript(hnwords[i]) || hnwords[i].length() < MinHnWordLen)
                    continue; // only hindi words as key
                
                TreeSet<String> translitWords = translitMap.get(hnwords[i]);                
                if (translitWords == null) {
                    translitWords = new TreeSet<>();
                }
                translitWords.add(enwords[i]);
                translitMap.put(hnwords[i], translitWords);

                WordSplitter ws = new WordSplitter(enwords[i], hnwords[i]);
                buff.append(ws.getMergedCharGrams());
                //buff.append(enwords[i]).append(" ").append(hnwords[i]).append(" ");
			}
        }
        try {
            indexer.concatDocFile.write(buff.toString());
        }
        catch (IOException ex) { System.err.println("Unable to write to words file"); }
    }
    
	public String toString() {
		StringBuffer buff = new StringBuffer();

		Iterator<String[]> eniter = enwords.iterator();
		Iterator<String[]> hniter = hnwords.iterator();

		while (eniter.hasNext() && hniter.hasNext()) {
			String[] enwords = eniter.next();
			String[] hnwords = hniter.next();
			int min = Math.min(enwords.length, hnwords.length);
			int max = enwords.length==min? hnwords.length : enwords.length;
			int i;
			for (i = 0; i < min; i++) {
				buff.append(enwords[i]).append(" ").append(hnwords[i]).append(" ");
			}
			while (i < max) {
				if (min == enwords.length)
					buff.append(hnwords[i]).append(" ");
				else
					buff.append(enwords[i]).append(" ");
				i++;
			}
			buff.append("\n");
		}
		// flesh out the remaining ones if any
		while (eniter.hasNext()) {
			String[] enwords = eniter.next();
			for (String enword : enwords) {
				buff.append(enword).append(" ");
			}
			buff.append("\n");
		}
		while (hniter.hasNext()) {
			String[] hnwords = hniter.next();
			for (String hnword : hnwords) {
				buff.append(hnword).append(" ");
			}
			buff.append("\n");
		}

		return buff.toString();
	}
    
    boolean isEn(String text) {
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'))
                return true;
        }
        return false;
    }
    
    String normalize(String word) {
        boolean isEn = isRomanScript(word);
        if (isEn)
            word = word.toLowerCase();
        word = word.replace("_", "");
        word = word.replace(".", "");
        word = word.replace(":", "");
        word = word.replace("^", "");
        if (!isEn) {
            // Remove anusvara and chandrabindoo
            word = word.replace("ँ", "");
            word = word.replace("ं", "");            
        }
        return word;
    }
    
    String normalizeWithSynSet(String word) {
        String retwords = new String(word + " ");
        HashMap<String, TreeSet<String>> translitMap = indexer.translitMap.getMap();
        // Get longest match in dict
        int len = word.length();
        String prefix = null;
        TreeSet<String> eqlist = null;
        for (int i = len; i >= MinHnWordLen; i--) {
            prefix = word.substring(0, i);
            eqlist = translitMap.get(prefix);
            if (eqlist != null) {
                if (i == len) {
                    retwords = eqlist.first();
                    return retwords;
                }
                break;
            }
        }
        if (eqlist != null) {
            String ceil = eqlist.ceiling(word);
            String floor = eqlist.floor(word);
            if (ceil != null && floor != null && ceil.equals(floor)) {
                retwords = prefix;
                return retwords;
            }
        }        
        return retwords;
    }
    
	void load() {        
		FileReader fr = null;
		BufferedReader br = null;
        StringBuffer buff = new StringBuffer();
        
        boolean ngrams = indexer.prop.getProperty("indexing_unit", "words").
                        equals("ngrams")? true : false;
        int n = Integer.parseInt(indexer.prop.getProperty("ngrams", "3"));                                
        NGramAnalyzer analyzer = new NGramAnalyzer(n);
        
		try {
			String line;
			fr = new FileReader(docFile);
			br = new BufferedReader(fr);
            int hCount = 0;
            StringBuffer header = new StringBuffer();
            
			while ((line = br.readLine()) != null) {
                buff.append(line).append("\n");
                if (line.startsWith("###"))
                    hCount++;
                // process the header separately
                if (hCount <= 2) {
                    if (hCount == 1) {
                        header.append(line).append(" ");
                    }
                    else {
                        // hCount = 2, and we have seen the entire header
                        String headerStr = header.toString();
                        headerStr = headerStr.replace("\n", "");
                        headerStr = headerStr.replace("#", "");
                        String[] headers = headerStr.split("-");
                        String[] titleWords = null;
                        
                        for (String thisHeader : headers) {
                            // It is here that we need to distinguish between
                            // the word flow and the n-grams flow
                            // Both the flows populate the titleWords array
                            if (!ngrams) {
                                StringTokenizer st = new StringTokenizer(thisHeader, Delims);
                                int numTokens = st.countTokens();
                                if (numTokens == 0)
                                    continue;
                                titleWords = new String[numTokens];
                                int i = 0;
                                while (st.hasMoreTokens()) {
                                    String word = st.nextToken();
                                    word = normalize(word);
                                    titleWords[i++] = word;
                                }
                            }
                            else {
                                // employ the analyzer to tokenize
                                TokenStream ts = analyzer.tokenStream("content", thisHeader);
                                CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
                                List<String> tokensList = new ArrayList<>();
                                ts.reset();
                                
                                while (ts.incrementToken()) {
                                    tokensList.add(termAtt.toString().trim());
                                }
                                ts.end();
                                ts.close();
                                
                                if (tokensList.size() == 0) {
                                    tokensList.add(thisHeader);
                                }
                                
                                titleWords = new String[tokensList.size()];
                                titleWords = tokensList.toArray(titleWords);                                
                            }
                            
                            if (isRomanScript(titleWords[0]))
                                enTitle.add(titleWords);
                            else
                                hnTitle.add(titleWords);                                
                        }
                        
                        normalizeContent(enTitle, hnTitle, txtEnTitle, txtHnTitle);
                        hCount++; // one more for the end ###
                    }
                    continue;
                }
                
                /*
                SimpleAnalyzer analyzer = new SimpleAnalyzer(Version.LUCENE_46);
                TokenStream ts = analyzer.tokenStream("line", line);
                CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
                List<String> tokensList = new ArrayList<>();
                ts.reset();
                while (ts.incrementToken()) {
                    tokensList.add(termAtt.toString());
                }
                ts.end();
                ts.close();
                String[] tokens = new String[tokensList.size()];
                tokens = tokensList.toArray(tokens);
                */
                
                // Now we are reading the text from the body
                List<String> tokensList = new ArrayList<>();
                if (!ngrams) {
                    StringTokenizer st = new StringTokenizer(line, Delims);
                    while (st.hasMoreTokens()) {
                        tokensList.add(st.nextToken());                    
                    }
                }
                else {
                    TokenStream ts = analyzer.tokenStream("content", line);
                    CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
                    ts.reset();
                    while (ts.incrementToken()) {
                        tokensList.add(termAtt.toString().trim());
                    }
                    ts.end();
                    ts.close();                    
                }
                String[] tokens = new String[tokensList.size()];
                tokens = tokensList.toArray(tokens);
                
				if (line.equals(""))
					continue;
                if (tokens.length < 1)
                    continue;
                
                boolean isEn = isRomanScript(tokens[0]);
                ArrayList<String[]> words = isEn? enwords : hnwords;
                
                // Preprocess the tokens
                for (int i = 0; i < tokens.length; i++) {
                    tokens[i] = normalize(tokens[i]);
                }
                words.add(tokens);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		finally {
			try {
				if (br!=null) br.close();
				if (fr!=null) fr.close();
			}
			catch (Exception ex) { }
		}
        rawText = buff.toString();
	}
    
    String getRawText() { return rawText; }
    
	static boolean isRomanScript(String word) {
		int len = word.length();
		for (int i = 0; i < len; i++) {
			char ch = word.charAt(i);
			if (!(ch >=0 && ch <= 127))
				return false;
		}
		return true;
	}
}
	
class Indexer {
    Properties prop;
    String data_dir;
    String index_dir;
    Analyzer analyzer;
    int pass;
    int normMode;
    FileWriter concatDocFile;
    int oov;
    
    // global equivalence map (key: hn) (value: list of translit roman script words)
    TranslitMap translitMap;
    
    static final int ShingleSize = 2;
    static final int NORM_MODE_WO_SYNSET = 1;
    static final int NORM_MODE_SYNSET = 2;
    
    void loadMaps() throws Exception {
        translitMap = new TranslitMap();
        if (pass == 2) {
            if (normMode == NORM_MODE_SYNSET)
                translitMap.translitMap = translitMap.load(prop.getProperty("synmap"));
            else {
                translitMap.translitMap = translitMap.load(prop.getProperty("wordmap"));
                // extend the map bidirectionally
                HashMap<String, TreeSet<String>> map = new HashMap<String, TreeSet<String>>(translitMap.translitMap);
                for (Entry<String, TreeSet<String>> entry : translitMap.translitMap.entrySet()) {
                    String key = entry.getKey();
                    TreeSet<String> words = entry.getValue();
                    
                    for (String word : words) {
                        TreeSet<String> revmappedword = new TreeSet<String>();
                        revmappedword.add(key);
                        revmappedword.add(words.first());
                        map.put(word, revmappedword);
                    }
                }
                translitMap.translitMap = map;
            }
        }
    }

	Indexer(String propFile) throws Exception {
        
        prop = new Properties();
        prop.load(new FileReader(propFile));
        this.data_dir = prop.getProperty("coll");
        this.index_dir = prop.getProperty("index");
        pass = Integer.parseInt(prop.getProperty("pass"));
        normMode = prop.getProperty("normalize", "translit").equals("translit")?
                NORM_MODE_WO_SYNSET : NORM_MODE_SYNSET;

        analyzer = new PerFieldAnalyzerWrapper(
                new ShingleAnalyzerWrapper(new WhitespaceAnalyzer(Version.LUCENE_46),
                Indexer.ShingleSize, Indexer.ShingleSize, "#", true, true));
        
        if (pass == 1) {
            String allDocsFileName = prop.getProperty("alldocs");
            if (allDocsFileName != null)
                concatDocFile = new FileWriter(allDocsFileName);
        }
        
        loadMaps();
	}

    Analyzer getAnalyzer() { return analyzer; }
    
    public void indexAll() throws Exception {        
    	IndexWriter writer = null;
        File dataDir = new File(data_dir);
        File indexDir = new File(index_dir);

        IndexWriterConfig iwcfg = new IndexWriterConfig(Version.LUCENE_46, analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        writer = new IndexWriter(FSDirectory.open(indexDir), iwcfg);

        indexDirectory(writer, dataDir);
        writer.close();
        
        if (pass == 1) {
            translitMap.save(prop.getProperty("wordmap"));
            concatDocFile.close();
        }
    }
	
    private void indexDirectory(IndexWriter writer, File dir) 
        throws Exception {
        File[] files = dir.listFiles();
        for (int i=0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                System.out.println("Indexing directory " + f.getName());
                indexDirectory(writer, f);  // recurse
            } else { 
                indexFile(writer, f);
            }
        }
    }

    void indexFile(IndexWriter writer, File f) throws Exception {
        
        String name = f.getName();        
        if (name.charAt(0) == '.')
            return;
        if (name.charAt(name.length()-1) == '~')
            return;
        
    	MSIRDoc msirDoc = new MSIRDoc(f, this);        
        msirDoc.load();
        if (pass == 1) {
            System.out.println("Reading words from file: " + name);
            msirDoc.addAlignments();
        }
        else {
            System.out.println("Indexing file: " + name);
        
            msirDoc.normalizeContent(msirDoc.enwords, msirDoc.hnwords, msirDoc.txtEn, msirDoc.txtHn);
            
            // Create an instance of the PGN parser
            Document doc = new Document();
            doc.add(new Field(MSIRDoc.FIELD_DOC_NAME, name,
                    Field.Store.YES, Field.Index.NOT_ANALYZED));
            doc.add(new Field(MSIRDoc.FIELD_DOC_RAW_TEXT, msirDoc.getRawText(),
                    Field.Store.YES, Field.Index.NOT_ANALYZED));
            
            doc.add(new Field(MSIRDoc.FIELD_TITLE_EN, msirDoc.txtEnTitle.toString(),
                    Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field(MSIRDoc.FIELD_EN, msirDoc.getEnTxt(),
                    Field.Store.YES, Field.Index.ANALYZED));
            
            doc.add(new Field(MSIRDoc.FIELD_TITLE_HN, msirDoc.txtHnTitle.toString(),
                Field.Store.YES, Field.Index.ANALYZED));
                
            doc.add(new Field(MSIRDoc.FIELD_HN, msirDoc.getHnTxt(),
                    Field.Store.YES, Field.Index.ANALYZED));

            writer.addDocument(doc);
        }
    }

    Properties getProperties() { return prop; }
    int getNormMode() { return normMode; }
    
	public static void main(String[] args) {
        String propFile;
		if (args.length < 1) {
            propFile = "init.properties";
		}
        else {
            propFile = args[0];
        }
        try {
            Indexer indexer = IndexerFactory.createIndexer(propFile);
            indexer.indexAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
	}
}
 