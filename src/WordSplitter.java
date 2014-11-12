
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Debasis
 */
public class WordSplitter {
    
    String enWord;
    String hnWord;

    static final String Delim = " ";
    
    public WordSplitter(String enWord, String hnWord) {
        this.enWord = enWord;
        this.hnWord = hnWord;
    }
    
    
    boolean isEnVowel(char ch) {
        return (ch=='a' || ch=='e' || ch=='i' || ch=='o' || ch=='u');
    }
    
    // Split an English word (transliterated in this case)
    // into space separated list of CVs (Consonant Vowels) 
    String enSplitCV(String word) {
        StringBuffer buff = new StringBuffer();
        int i, j, len = word.length();
        buff.append(word.charAt(0));
        char ch = word.charAt(0);
        final int INIT = 0;
        final int CONSONANT_SEEN = 1;
        final int VOWEL_SEEN = 2;
        final int VC_SPLIT = 3;
        final int CV_SEEN = 4;
        final int CANDRA_SEEN = 5;
        int state = INIT;
        
        for (i = 1; i < len; i++) {
            ch = word.charAt(i);
            if (!isEnVowel(ch)) {
                if (ch == 'n' && (state == VOWEL_SEEN || state == CV_SEEN))
                    state = CANDRA_SEEN;
                else if (state == INIT || state == CONSONANT_SEEN)
                    state = CONSONANT_SEEN;
                else if (state == CV_SEEN || state == VOWEL_SEEN)
                    state = VC_SPLIT;
            }            
            else {
                if (state == INIT || state == VOWEL_SEEN)
                    state = VOWEL_SEEN;
                else if (state == CONSONANT_SEEN)
                    state = CV_SEEN;
            }
            
            switch (state) {
                case CONSONANT_SEEN:
                case VOWEL_SEEN: 
                case CV_SEEN:
                    buff.append(ch);
                    break;
                case CANDRA_SEEN:
                    buff.append(ch);
                    state = VC_SPLIT;
                    break;
                case VC_SPLIT:
                    buff.append(Delim);
                    buff.append(ch);
                    state = INIT;
                    break;
            }
        }
        
        len = buff.length();
        if (len > 3 &&
             !isEnVowel(buff.charAt(len-1)) &&
             buff.charAt(len-2) == ' ' &&
             isEnVowel(buff.charAt(len-3))) {
            buff.deleteCharAt(len-2);
        }
        
        return buff.toString();
    }

    int isHindiVowel(char x) {
        if (x >= 0x0905 && x <= 0x0914)
            return 1;
        if (x >= 0x093e && x <= 0x094c)
            return 2;
        if (x == 0x092f)
            return 2;
        return 0;
    }
    
    // Split a Hindi word into V (stand-alone vowel) or Cv
    // consonant with a matra
    String hnSplitCV(String word) {
        StringBuffer buff = new StringBuffer();
        int i, len = word.length();
        char prevch, ch = word.charAt(0);
        boolean split = false;
        boolean splitAtNext = false;
        int lastiter = -1;
        buff.append(ch);
        
        for (i = 1; i < len; i++) {
            split = false;
            prevch = ch;
            ch = word.charAt(i);
            buff.append(ch);
            
            if (splitAtNext) {
                buff.append(Delim);
                lastiter = i;
                splitAtNext = false;
                continue;
            }
            
            int prevVowelType = isHindiVowel(prevch);
            int thisVowelType = isHindiVowel(ch);

            if (prevVowelType == 0 && thisVowelType == 2)
                split = true;
            if (prevVowelType == 0 && thisVowelType == 0 && lastiter < i-1)
                split = true;
            if (i - lastiter > 1)
                split = true;

            if (split) {
                if (i < len-1) {
                    char nextchar = word.charAt(i+1);
                    if (nextchar == 0x094d || nextchar == 0x0939) {
                        splitAtNext = true;
                        continue;
                    }
                }
                buff.append(Delim);
                lastiter = i;
            }            
        }
        
        len = buff.length();
        for (i = 1; i < len; i++) {
            ch = buff.charAt(i);
            prevch = buff.charAt(i-1);
            if (isHindiVowel(ch)==2 && prevch == Delim.charAt(0)) {
                buff.deleteCharAt(i-1);
                len--;
            }
        }
        return buff.toString();
    }
    
    String getMergedCharGrams() {
        StringBuffer merged = new StringBuffer();
        String[] hnGrams = hnSplitCV(hnWord).split(Delim);
        String[] enGrams = enSplitCV(enWord).split(Delim);
        //String[] hnGrams = splitIntoNGrams(hnWord, 3).split(Delim);
        //String[] enGrams = splitIntoNGrams(enWord, 3).split(Delim);
        int min = Math.min(enGrams.length, hnGrams.length);
        int max = Math.max(enGrams.length, hnGrams.length);
        int i;
        
        for (i = 0; i < min; i++) {
            merged.append(hnGrams[i]).append(Delim).append(enGrams[i]).append(Delim);
        }
        for (i = min; i < max; i++) {
            merged.append(min==enGrams.length? hnGrams[i] : enGrams[i]).append(Delim);
        }        
        return merged.toString();
    }
    
    // Split into character n-grams
    String splitIntoNGrams(String word, int n) {
        StringBuffer buff = new StringBuffer();
        /*
        int len = word.length(), c = 0;
        for (int i = 0; i < len; i++) {
            if (c == n) {
                buff.append(Delim);
                c = 0;
            }
            c++;
            buff.append(word.charAt(i));
        }
        */
        try (NGramTokenizer ts = new NGramTokenizer(
                Version.LUCENE_46, new StringReader(word), 3, 3)) {
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            
            ts.reset();
            while (ts.incrementToken()) {
                buff.append(termAtt.toString()).append(Delim);
            }
            buff.append(Delim);
            ts.end();
        }
        catch (IOException ex) { } 
        
        return buff.toString();
    }
    
    // unit test
    public static void main(String[] args) {
        try {
            WordSplitter ws = null;
            // read the wordmap file
            FileReader fr = new FileReader("seplines.txt");
            BufferedReader br = new BufferedReader(fr);
            String line;
            int count = 0;
            
            while (count++ < 100) {
                line = br.readLine();
                String[] tokens = line.split("\\s+");
                String hnWord = tokens[0];
                String enWord = tokens[1];
                ws = new WordSplitter(enWord, hnWord);
                System.out.println(ws.getMergedCharGrams());
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
}
