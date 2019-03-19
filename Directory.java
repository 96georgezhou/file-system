
import java.util.*;
import java.io.*;
import java.lang.*;

/**
 * 
 * Directory.java
 * 
 * George Zhou and Gahl Goziker
 * CSS 430
 * March 2019
 *
 */
public class Directory {
    private final static int maxChars = 30;                 // Max fnames chars
    private final static int maxJava = 60;                  // Max Java bytes
    private final static int BYTE_ALLOC = 64;               // MaxJava + 4 short
    private final static int NEXT_CHUNK = 4;                // Used for offset
    private final static int ERROR = -1;                    // For clean reading
    private int dirSize;                                    // Directory size
    private int fsizes[];                                   // File sizes
    private char fnames[][];                                // File names

    private final static boolean SUCCESS = true;
    private final static boolean FAILURE = false;

    /**
     * Constructor
     * @param maxInumber
     */
    public Directory( int maxInumber ) {          
        fsizes = new int[maxInumber];                       // Max files to store
        for ( int i = 0; i < maxInumber; i++ ){
            fsizes[i] = 0;                                  // File sizes all initialized to 0
        }
        dirSize = maxInumber;                               // Directory Size
        fnames = new char[maxInumber][maxChars];            // Create Files
        String root = "/";                                  // First entry is '/', which is the root
        fsizes[0] = root.length( );
        root.getChars( 0, fsizes[0], fnames[0], 0 );    // Put in [0]
    }

    /**
     * Read in Byte array from the disk to set up the directory
     * @param data
     */
    public void bytes2directory( byte[] data ) {
        int offset = 0;                                     // Offset to 0
        for(int i = 0; i < dirSize; i++){                   // Loop through the directory
            fsizes[i] = SysLib.bytes2int(data, offset);     // Save File size
            offset += NEXT_CHUNK;                           // Offset increment
        }
        for(int i = 0; i < dirSize; i++){                   // Loop through the directory
            String tmpS = new String(data, offset, maxJava);//create a string ob
            tmpS.getChars(0, fsizes[i], fnames[i], 0);      //place in fnames[i]
            offset += maxJava;                              //increment offset
        }
    }

    /**
     * Convert Directory instance to Byte Object
     * @return
     */
    public byte[] directory2bytes( ) {
        byte[] dirInfo = new byte[BYTE_ALLOC * dirSize];    // temp Byte array for return

        int offset = 0;                                     // init the offset
        for(int i = 0; i < dirSize; i++){                   // Loop thru directory
            SysLib.int2bytes(fsizes[i], dirInfo, offset);   // Populate the directory
            offset += NEXT_CHUNK;                           // Increment Offset
        }
        for(int i = 0; i < dirSize; i++){                   // Loop thru Directory
            String tmpS = new String(fnames[i],0,fsizes[i]);
            byte[] tmpByte = tmpS.getBytes();               // to Byte
            System.arraycopy(tmpByte, 0, dirInfo, offset, tmpByte.length);
            offset += maxJava;                              // Increment Offset
        }
        return dirInfo;
    }

    /**
     * Allocate Inode for file specified by name
     * @param filename
     * @return
     */
    public short ialloc( String filename ) {
        for(short i = 0; i < dirSize; i++){                 // Loop thru directory
            if(fsizes[i] == 0){                             // Check for empty file
                int fs = filename.length()>maxChars?maxChars:filename.length();
                fsizes[i] = fs;                             // Save File Size
                filename.getChars(0,fsizes[i],fnames[i],0); // Copy data from String
                return i;                                   // Return Inumber
            }
        }
        return ERROR;                                       // All Spaces have been allocated
    }

    /**
     * Free up the space specified by the iNumber
     * @param iNumber
     * @return
     */
    public boolean ifree( short iNumber ) {
        if(iNumber < maxChars && fsizes[iNumber] > 0){      // Valid iNumber
            fsizes[iNumber] = 0;                            // Delete by setting the file size to 0
            return SUCCESS;                                 // Found and freed up
        } else {                                     
            return FAILURE;                                 // Invalid iNumber
        }
    }

    /**
     * Search for the index of the file by fileName
     * @param filename
     * @return index of the file
     */
    public short namei( String filename ) {
     for(short i = 0; i < dirSize; i++){                    // Loop thru directory
        if(filename.length() == fsizes[i]){                 // Equal String size
            String tmp = new String(fnames[i],0,fsizes[i]); // String created
            if(filename.equals(tmp)) return i;              // return index
        }
    }
    return ERROR;                                           // No such file found
    }

    /**
     * Print the whole directory in 2-d array
     */
    private void printDirectory(){
        for(int i = 0; i < dirSize; i++){                   // Loop from top to bottom
            SysLib.cout(i+"[" + fsizes[i] + "]  ");      // Print file size
            for(int j = 0; j < maxChars; j++){
                SysLib.cout(fnames[i][j] + " ");         // Output the contents
            }
            SysLib.cout("\n");
        }
    }    
}
