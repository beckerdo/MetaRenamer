package info.danbecker.metarenamer;

import org.apache.tika.cli.TikaCLI;

/**
 *
 * Print the supported Tika Metadata models and
 * their fields.
 *
 */
public class DescribeMetadata {
  
  public static void main(String [] args) throws Exception{
     TikaCLI.main(new String[]{"--list-met-models"});
  }

}