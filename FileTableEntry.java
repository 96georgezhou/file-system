//
// Created by george on 03/16/19.
//

public class FileTableEntry {
   public int seekPtr;                 // seek pointer
   public final Inode inode;           // reference to its inode
   public final short iNumber;         // inode number
   public int count;
   public final String mode;           // "r", "w", "w+", or "a"
   
   public FileTableEntry ( Inode i, short inumber, String m ) {
      seekPtr = 0;             // top of the file
      inode = i;
      iNumber = inumber;
      count = 1;               // using this entry
      mode = m;                // never change
      if ( mode.compareTo( "a" ) == 0 ) // append
         seekPtr = inode.length;        // seekPtr points to the end of file
   }
}
