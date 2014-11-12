/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Debasis
 */
import java.util.regex.*;

class TranslitNormalizer {

    public static String normalize(String text) {
        String norm = null;
        Pattern p = null;
        Matcher m = null;
        StringBuffer otext = new StringBuffer();

        // Normalize the 'h' sounds
        text = text.replaceAll("hh", "h");
        text = text.replaceAll("v", "bh");
        text = text.replaceAll("bh", "b");
        text = text.replaceAll("cch", "c");
        text = text.replaceAll("cch", "c");
        text = text.replaceAll("ch", "c");
        text = text.replaceAll("gh", "g");
        text = text.replaceAll("jh", "j");
        text = text.replaceAll("sh", "s");
        text = text.replaceAll("th", "t");
        text = text.replaceAll("um", "am");
        /*
        if (text.equals("ham"))
            return "hum";
        */
        
        // put in an 'a' between two consonants
        p = Pattern.compile("([bcdfgjklmnpqrstvwxz])([bcdfgjklmnpqrstvwxz])");
        m = p.matcher(text);
        while (m.find()) {
            m.appendReplacement(otext, m.group(1) + "a" + m.group(2));
        }
        m.appendTail(otext);
        norm = otext.toString();

        otext = new StringBuffer();
        p = Pattern.compile("([aeiou])([y])([aeiou])");
        m = p.matcher(norm);
        boolean replaced = false;
        while (m.find()) {
            m.appendReplacement(otext, m.group(1) + m.group(3));
            replaced = true;
        }
        if (replaced) {
            m.appendTail(otext);
            norm = otext.toString();
        }

        // replace <something>d[h]<something> with <something>r<something>
        otext = new StringBuffer();
        p = Pattern.compile("([a-z])(d)([a-z])");
        m = p.matcher(norm);
        replaced = false;
        while (m.find()) {
            m.appendReplacement(otext, m.group(1) + "r" + m.group(3));
            replaced = true;
        }
        if (replaced) {
            m.appendTail(otext);
            norm = otext.toString();
        }

        otext = new StringBuffer();
        p = Pattern.compile("([a-z])(dh)([a-z])");
        m = p.matcher(norm);
        replaced = false;
        while (m.find()) {
            m.appendReplacement(otext, m.group(1) + "r" + m.group(3));
            replaced = true;
        }
        if (replaced) {
            m.appendTail(otext);
            norm = otext.toString();
        }

        // remove all instances of <something><vowel>.h.<vowel>
        otext = new StringBuffer();
        p = Pattern.compile("([aeiou])(h)([aeiou])");
        m = p.matcher(norm);
        replaced = false;
        while (m.find()) {
            m.appendReplacement(otext, m.group(1) + "h");
            replaced = true;
        }
        if (replaced) {
            m.appendTail(otext);
            norm = otext.toString();
        }
        
        // remove the y from vowel.y.vowel
        norm = norm.replaceAll("q", "k");
        norm = norm.replaceAll("ia", "ya");
        norm = norm.replaceAll("ay", "ai");
        norm = norm.replaceAll("ae", "ai");
        norm = norm.replaceAll("eh", "ah");
        norm = norm.replaceAll("aa", "a");
        norm = norm.replaceAll("ii", "i");
        norm = norm.replaceAll("ee", "i");
        norm = norm.replaceAll("oo", "u");
        norm = norm.replaceAll("uu", "u");
        norm = norm.replaceAll("ei", "e");
        norm = norm.replaceAll("z", "j");
        norm = norm.replaceAll("v", "w");
        norm = norm.replaceAll("ain", "ai");
        norm = norm.replaceAll("yun", "yu");
        
        int len = norm.length();
        if (len > 2 && norm.charAt(len-1) == 'a')
            norm = norm.substring(0, len-1);

        if (norm.endsWith("ah") || norm.endsWith("eh") || norm.endsWith("oh") ||
            norm.endsWith("ih") || norm.endsWith("uh") )
            if (norm.length() > 3)
                norm = norm.substring(0, len-2);
        
        return norm;
    }
    
    public static void main(String[] args) {
        System.out.println(TranslitNormalizer.normalize("bicchar"));
    }
}
              
