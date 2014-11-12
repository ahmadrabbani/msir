
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Properties;
import java.util.TreeSet;
import java.util.SortedMap;
import java.util.TreeSet;
import org.apache.commons.collections4.trie.PatriciaTrie;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Debasis
 * Load the saved transliterated map into memory and extract the
 * equivalent Hindi words using a prefix tree.
 * 
 */
public class TranslitMap {
   
    HashMap<String, TreeSet<String>> translitMap;
    String fileName;
    static final int MaxSuffixLen = 2;
    
    public TranslitMap() throws Exception {
        translitMap = new HashMap<>();
    }
    
    public TranslitMap(String fileName) throws Exception {
        this.fileName = fileName;
        translitMap = load(fileName);
    }
    
    HashMap<String, TreeSet<String>> getMap() { return translitMap; }
    
    HashMap<String, TreeSet<String>> load(String fileName) throws Exception {
        HashMap<String, TreeSet<String>> translitMap = new HashMap<>();
        FileReader fr = new FileReader(fileName);
        BufferedReader br = new BufferedReader(fr);
        String line;
        
        while ((line = br.readLine()) != null) {
            String[] tokens = line.split("\\s+");
            if (tokens.length < 2)
                continue;
            
            String[] translits = tokens[1].split(",");
            TreeSet<String> translitSet = new TreeSet<>();
            for (String translit : translits)
                translitSet.add(translit);
            
            translitMap.put(tokens[0], translitSet);
        }
        br.close();
        fr.close();
        return translitMap;
    }
        
    // Process the transliteration map in an attempt to group
    // together similar words. 
    void processTranslitMap() throws Exception {
        PatriciaTrie<TreeSet<String>> trie = new PatriciaTrie(translitMap);
        HashMap<String, TreeSet<String>> normalizedMap = new HashMap<>();
        
        for (String key = trie.firstKey(); key != null; key = trie.nextKey(key)) {
            TreeSet<String> wordSet = translitMap.get(key);
            // iterate for every hindi word and get it's prefixes
            SortedMap<String, TreeSet<String>> prefixes = trie.prefixMap(key);
            for (String prefix : prefixes.keySet()) {
                if (Math.abs(key.length() - prefix.length()) > MaxSuffixLen)
                    continue;
                // check how many of these are valid words
                TreeSet<String> equivalentWords = translitMap.get(prefix);
                if (equivalentWords == null)
                    continue;
                // the prefix is also a valid word...
                // insert this word and its transliteations
                // in the equivance set of the prefix
                // remove the prefix
                wordSet.add(prefix);
                equivalentWords.clear();
            }
            normalizedMap.put(key, wordSet);
        }
        
        for (String key : normalizedMap.keySet()) {
            TreeSet<String> eqwords = normalizedMap.get(key);
            if (eqwords == null || eqwords.size() == 0) {
                translitMap.remove(key);
            }
            else {
               TreeSet<String> synset = translitMap.get(key);
               if (synset != null) {
                   for (String eqword : eqwords) {
                       if (Math.abs(eqword.length() - key.length()) <= MaxSuffixLen)
                           synset.add(eqword);
                       translitMap.remove(eqword);
                   }
               }
            }
        }

        HashMap<String, TreeSet<String>> origtranslitMap = load(fileName);
        // Now add the translit words from the orig map
        for (String key : origtranslitMap.keySet()) {
            TreeSet<String> translits = origtranslitMap.get(key);
            TreeSet<String> wordlist = translitMap.get(key);
            if (wordlist != null) {
                if (translits != null)
                    wordlist.addAll(translits);
                TreeSet<String> tmpList = new TreeSet<String>(wordlist);
                for (String word : wordlist) {
                    translits = origtranslitMap.get(word);
                    if (translits != null)
                        tmpList.addAll(translits);                        
                }
                wordlist.addAll(tmpList);
                
                // Make a bidirectional map, i.e. enable roman
                // script words searching in the map and replacing the
                // matched ones with the synset
                String translitKey = wordlist.first();
                TreeSet<String> wordlistCopy = new TreeSet<>(wordlist);
                wordlistCopy.remove(translitKey);
                wordlistCopy.add(key);
                translitMap.put(translitKey, wordlistCopy);
            }
        }
    }
 
    void save(String fileName) throws Exception {
        FileWriter fw = new FileWriter(fileName);        
        for (String hnword : translitMap.keySet()) {
            StringBuffer buff = new StringBuffer();
            TreeSet<String> enwords = translitMap.get(hnword);
            if (enwords == null)
                continue;
            buff.append(hnword).append("\t");
            for (String enword : enwords) {
                buff.append(enword).append(",");
            }
            buff.deleteCharAt(buff.length()-1);
            buff.append("\n");
            fw.write(buff.toString());
        }
        fw.close();
    }

    public static void main(String[] args) {
        try {            
            String propFile = args.length == 0? "init.properties" : args[0];
            Properties prop = new Properties();
            prop.load(new FileReader(propFile));
            
            TranslitMap normalizer = new TranslitMap(prop.getProperty("wordmap"));
            normalizer.processTranslitMap();
            normalizer.save(prop.getProperty("synmap"));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
