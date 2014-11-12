
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Reads from a word2vec file and expands the
 * query with the k-NN set of terms...

 * @author dganguly
 */

class Wordvec implements Comparable<Wordvec> {
    String word;
    float[] vec;
    float norm;
    float querySim; // distance from a reference query point
    
    Wordvec(String word, float[] vec) {
        this.word = word;
        this.vec = vec;
    }
    
    Wordvec(String line) {
        String[] tokens = line.split("\\s+");
        word = tokens[0];
        vec = new float[tokens.length-1];
        for (int i = 1; i < tokens.length; i++)
            vec[i-1] = Float.parseFloat(tokens[i]);
        norm = getNorm();
    }
    
    float getNorm() {
        if (norm == 0) {
            // calculate and store
            float sum = 0;
            for (int i = 0; i < vec.length; i++) {
                sum += vec[i]*vec[i];
            }
            norm = (float)Math.sqrt(sum);
        }
        return norm;
    }
    
    float cosineSim(Wordvec that) {
        float sum = 0;
        for (int i = 0; i < this.vec.length; i++) {
            sum += vec[i] * that.vec[i];
        }
        return sum / (this.norm*that.norm);
    }

    @Override
    public int compareTo(Wordvec that) {
        return this.querySim > that.querySim? -1 : this.querySim == that.querySim? 0 : 1;
    }
}

public class Word2VecQE {

    Properties prop;
    int k;
    HashMap<String, Wordvec> wordvecmap;

    public Word2VecQE(Properties prop) throws Exception {
        this.prop = prop;
        wordvecmap = new HashMap();
        k = Integer.parseInt(prop.getProperty("qexp.wvec.numwords", "3"));
        
        String wordvecFile = prop.getProperty("wordvecs.vecfile");
        FileReader fr = new FileReader(wordvecFile);
        BufferedReader br = new BufferedReader(fr);
        String line;
        
        while ((line = br.readLine()) != null) {
            Wordvec wv = new Wordvec(line);
            wordvecmap.put(wv.word, wv);
        }
        
        br.close();
        fr.close();
    }
    
    public Word2VecQE(String propFile) throws Exception {
        
        prop = new Properties();
        prop.load(new FileReader(propFile));
        k = Integer.parseInt(prop.getProperty("qexp.wvec.numwords", "3"));
        
        wordvecmap = new HashMap();        
        String wordvecFile = prop.getProperty("wordvecs.vecfile");
        FileReader fr = new FileReader(wordvecFile);
        BufferedReader br = new BufferedReader(fr);
        String line;
        
        while ((line = br.readLine()) != null) {
            Wordvec wv = new Wordvec(line);
            wordvecmap.put(wv.word, wv);
        }
        
        br.close();
        fr.close();
    }
    
    Wordvec[] getNearestNeighbors(String queryWord) {
        Wordvec[] wlist = new Wordvec[k];
        ArrayList<Wordvec> distList = new ArrayList<>(wordvecmap.size());
        
        Wordvec queryVec = wordvecmap.get(queryWord);
        if (queryVec == null)
            return null;
        
        for (Map.Entry<String, Wordvec> entry : wordvecmap.entrySet()) {
            Wordvec wv = entry.getValue();
            if (wv.word.equals(queryWord))
                continue;
            wv.querySim = queryVec.cosineSim(wv);
            distList.add(wv);
        }
        Collections.sort(distList);
        wlist = distList.subList(0, k).toArray(wlist);        
        return wlist;
    }
    
    public static void main(String[] args) {
        try {
            Word2VecQE qe = new Word2VecQE("init.properties");
            Wordvec[] nwords = qe.getNearestNeighbors("mere");
            for (Wordvec word : nwords) {
                System.out.println(word.word + "\t" + word.querySim);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
