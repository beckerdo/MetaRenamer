package info.danbecker.metarenamer;

import org.apache.tika.cli.TikaCLI;

/**
 *
 * Print CLI help to the console.
 *
 */
public class TikaCLIHelp {
  
  public static void main(String [] args) throws Exception{
     TikaCLI.main(new String[]{"--help"});
  }

}