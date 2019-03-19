/**
 * 
 * Inode.java
 * 
 * George Zhou and Gahl Goziker
 * CSS 430
 * March 2019
 *
 */
public class Inode {
   private final static int iNodeSize = 32;       // inode byte size in disk
   public final static int directSize = 11;      // # direct pointers
   private final static int maxBytes = 512;

   public int length;                             // file size in bytes
   public short count;                            // # file-table entries pointing to this
   public short flag;                             // 0 = unused, 1 = used, ...
   public short direct[] = new short[directSize]; // direct pointers
   public short indirect;                         // a indirect pointer

   
   
   // -------------------------------------------------------------------------
   // Inode()						default constructor
   /**
    * 
    * Sets inode length and count to 0, sets flag to 1 (used), sets all
    * pointers to -1
    * 
    */
   Inode( ) {
      length = 0;
      count = 0;
      flag = 1;
      for ( int i = 0; i < directSize; i++ )
         direct[i] = -1;
      indirect = -1;
   }

   
   
   // -------------------------------------------------------------------------
   // Inode(short iNumber)			constructor
   /**
    * 
    * Read a byte array from the disk which represents the inode.
    * Initialize the inode based on this byte array.
    * 
    * 
    * Format of the byte array is:
    * 		The first 4-byte chunk stores an ints representing the file size
    * 		in bytes.
    * 		The next 2-byte chunk stores count of file-table entries pointing
    * 		to this inode.
    * 		The next 2-byte chunk stores flag
    * 		The next 11 2-byte chunks store direct pointers to files in disk
    * 		The next 2-byte chunk stores pointer to indirect block
    * 
    * 
    * @param iNumber							// index of the inode in disk
    */
   Inode( short iNumber ) {
      // figure out index of disk block storing inode
      int blkContainingInode = 1 + iNumber / 16; // 1 block stores 16 inodes
      byte[] data = new byte[maxBytes];
      SysLib.rawread(blkContainingInode,data);

      // Index in block where this inode begins
      int offset = (iNumber % 16) * iNodeSize;

      // Allocate space for data members
      length = SysLib.bytes2int(data,offset);
      offset +=4;
      count = SysLib.bytes2short(data,offset);
      offset +=2;
      flag = SysLib.bytes2short(data,offset);
      offset +=2;

      // Allocate space for pointers
      for (int i = 0; i < directSize; i++) {
         direct[i] = SysLib.bytes2short(data,offset);
         offset +=2;
      }
      indirect = SysLib.bytes2short(data,offset);
      offset +=2;
   }

   // -------------------------------------------------------------------------
   // toDisk(short iNumber)
   /**
    * 
    * Convert and return inode information into a plain byte array, which
    * will be written back to the disk.
    * 
    * 
    * Format of the byte array is:
    * 		The first 4-byte chunk stores an ints representing the file size
    * 		in bytes.
    * 		The next 2-byte chunk stores count of file-table entries pointing
    * 		to this inode.
    * 		The next 2-byte chunk stores flag
    * 		The next 11 2-byte chunks store direct pointers to files in disk
    * 		The next 2-byte chunk stores pointer to indirect block
    * 
    * 
    * @param iNumber							// index of the inode in disk
    * @return
    */
   void toDisk( short iNumber ) {
      // initialize buffer size
      byte[] inodeInfo = new byte[iNodeSize];

      int offset = 0;

	  // Allocate space for data members
      SysLib.int2bytes(length, inodeInfo, offset);
      offset +=4;
      SysLib.short2bytes(count, inodeInfo, offset);
      offset +=2;
      SysLib.short2bytes(flag, inodeInfo, offset);
      offset +=2;

      // Allocate space for pointers
      for (int i = 0; i < directSize; i++) {
         SysLib.short2bytes(direct[i], inodeInfo, offset);
         offset +=2;
      }
      SysLib.short2bytes(indirect, inodeInfo, offset);
      offset +=2;

	  ////////////////////////////////////////////////////
	  // Ensure that the other inodes in the block don't get overwritten
	  ////////////////////////////////////////////////////
	   
	  // Read the block from disk
      int blkContaiingInode = 1 + iNumber / 16; 
      byte[] newData = new byte[maxBytes];
      SysLib.rawread(blkContaiingInode,newData);

	  // Overwrite this inode's data in block
      offset = (iNumber % 16) * iNodeSize;
      System.arraycopy(inodeInfo, 0, newData, offset, iNodeSize);
      
      // Write block back to disk
      SysLib.rawwrite(blkContaiingInode,newData);
   }
   
   
   
   // -------------------------------------------------------------------------
   // freeIndirectBlock
   /**
    * 
    * Deallocates indirect block, returns its contents
    * 
    * 
    * @return
    */
   byte[] freeIndirectBlock()
   {
     if (indirect >= 0) {
       byte[] indirectBlockContents = new byte[maxBytes];
       SysLib.rawread(indirect, indirectBlockContents);
       indirect = -1;
       return indirectBlockContents;
     }
     else
       return null; //nothing to free
   }
   
   
   
   	// -------------------------------------------------------------------------
   	// getIndexBlockNumber
	/**
	 *
	 * @param entry
	 * @param offset
	 * @return
	 * 0 = unused
	 * -1 = error on write to used block
	 * -2 = error on write to unused block
	 * -3 = error on write to null pointer
	 */
   int getIndexBlockNumber(int entry, short offset){
    int targetBlock = entry / maxBytes;

    // If entry should be in a block w a direct pointer
    if (targetBlock < directSize){
      if (direct[targetBlock] >= 0){
        return -1;
      }
      //check if direct pointer is pointing to
      if((targetBlock > 0) && (direct[(targetBlock - 1)] == -1)){
        return -2;
      }
      direct[targetBlock] = offset;
      return 0; //unused
    }

    // If there is no indirect block
    if (indirect < 0){
      return -3;
    }
    
    // If there is an indirect block
    else{
    	
      // Read contents of indirect block
      byte[] data = new byte[maxBytes];
      SysLib.rawread(indirect,data);

      int offsetInIndirectBlk = (targetBlock - directSize) * 2;
      if (SysLib.bytes2short(data, offsetInIndirectBlk) > 0){
        return -1;
      }
      else{
        SysLib.short2bytes(offset, data, offsetInIndirectBlk);
        SysLib.rawwrite(indirect, data);
      }
    }
    return 0;
   }

   
   
   // -------------------------------------------------------------------------
   // setIndexBlock
   /**
    * 
    * Sets the index block ptr to point to the passed block number, but
    * only if all direct ptrs are occupied and index block ptr is unoccupied
    * 
    * 
    * If index block indirect pointer is not set to -1,
    * or if all the direct pointers are = -1 then return false.
    *  
    * If not, point the indirect pointer to the passed index block,
    * and return true.
    *   
    * @param indexBlockNumber
    * @return
    */
   boolean setIndexBlock(short indexBlockNumber){

	// Don't set the indirect block if there's available direct ptrs
    for (int i = 0; i < directSize; i++){
      if (direct[i] == -1)
        return false;
    }
    // Don't set the indirect block if it's already set
    if (indirect != -1)
      return false;

    indirect = indexBlockNumber;
    byte[] data = new byte[maxBytes];
    for (int i = 0; i < (maxBytes/2); i++){
        SysLib.short2bytes((short) -1, data, i*2);
    }
    SysLib.rawwrite(indexBlockNumber, data);

    return true;
   }

   
   
   /**
    * 
    * Search for the block within a file which contains a given offset.
    * 
    * 
    * If found, return ptr to the target block. If not, return -1.
    * 
    * 
    * @param offset
    * @return
    */
   int findTargetBlock(int offset){ 
      int targetBlockNum = offset / maxBytes;
      // Return the specified block
      if (targetBlockNum < directSize)
        return direct[targetBlockNum];

      if (indirect < 0)
        return -1;

      // Read indirect block from disk
      byte[] data = new byte[maxBytes];
      SysLib.rawread(indirect, data);

      // Return the specified block
      int offsetInIndirectBlock = (targetBlockNum - directSize) *2;
      return SysLib.bytes2short(data, offsetInIndirectBlock);
   }

}

