package info.danbecker.metarenamer;

import java.io.File;

import org.apache.tika.Tika;

public class SimpleTypeDetector {

  public static void main(String[] args) throws Exception {
      Tika tika = new Tika();

      for (String file : args) {
    	  System.out.print( "File=" + new File(file));
          String type = tika.detect(new File(file));
          System.out.println( ", type=\"" + type + "\"");
      }
  }

}
