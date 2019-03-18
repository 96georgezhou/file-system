/**
 * The first disk block, block 0, is called the superblock. It is used to describe
 * 		The number of disk blocks.
 * 		The number of inodes.
 * 		The block number of the head block of the free list.
 * It is the OS-managed block. No other information must be recorded in and no user threads
 * must be able to get access to the superblock.
 */
public class SuperBlock{

	public int totalBlocks; // the number of disk blocks
    public int totalInodes; // the number of inodes
    public int freeList;    // the block number of the head block of the free list
    
	private final int defaultInodeBlocks = 64;
	private final int totalBlockLocation = 0;
	private final int totalInodeLocation = 4;
	private final int freeListLocation = 8;
	private final int defaultBlocks = 1000;


    
    
    /**
     * constructor for SuperBlock
     * @param numBlocks
     */
	public SuperBlock(int numBlocks){
		byte[] superBlock = new byte[Disk.blockSize];	// read superblock from disk
		
		SysLib.rawread(0, superBlock);		// Superblock located in index 0

		totalBlocks = SysLib.bytes2int(superBlock,totalBlockLocation);
		totalInodes = SysLib.bytes2int(superBlock,totalInodeLocation);
		freeList = SysLib.bytes2int(superBlock,freeListLocation);

		
		if(totalBlocks == numBlocks && totalInodes > 0 && freeList >= 2){
			return;
		}
		else{
			totalBlocks = numBlocks;
			format(defaultInodeBlocks);
		}
	}

	
	
	/**
     * Reload the superBlock from the disk
     * Update   totalBlocks totalInodes freelist
     */
	public void sync(){
		byte[] superBlockData = new byte[Disk.blockSize];
		
		SysLib.int2bytes(totalBlocks,superBlockData,totalBlockLocation);
		SysLib.int2bytes(totalInodes,superBlockData,totalInodeLocation);
		SysLib.int2bytes(freeList,superBlockData,freeListLocation);

		// write superblock data to disk
		SysLib.rawwrite(0,superBlockData);
	}

	
	
	/**
     * get the first freeBlock
     * @return -1 if invalid
     */
	public int getFreeBlock(){
		// Check that freeList is valid
		if(freeList > 0 && freeList < totalBlocks){
			byte[] tempBlock = new byte[Disk.blockSize];
			SysLib.rawread(freeList, tempBlock);	// read free block into tempBlock
			int retVal = freeList;
			freeList = SysLib.bytes2int(tempBlock, 0);
			return retVal;	
		}
		return -1;
	} 

	
	
	/**
	 * Attempt to free the block at the specified block index.
	 * 
	 * 
	 * @param blockNumber
	 * @return False if operation fails
	 */
	public boolean returnBlock(int blockNumber){
		// If valid block number
		if(blockNumber > 0 && blockNumber < totalBlocks){
			int nextFreeBlockNum = freeList;
			int temp = 0;
			byte[] nextFreeBlock = new byte[Disk.blockSize];
			// Block being appended to free list
			byte[] newFreeBlock = new byte[Disk.blockSize];

			// Clear the contents of the block being freed
			for(int i = 0; i < Disk.blockSize; i++){
				newFreeBlock[i] = 0;
			}

			// Last block in free list must point to -1
			SysLib.int2bytes(-1,newFreeBlock,0);

			// Traverse to end of free list
			while(nextFreeBlockNum != -1){
				
				// Get next block
				SysLib.rawread(nextFreeBlockNum, nextFreeBlock);
				temp = SysLib.bytes2int(nextFreeBlock,0);

				// If the end of the free list is reached
				if(temp == -1){
					// Point last free block to the new free block
					SysLib.int2bytes(blockNumber,nextFreeBlock,0);
					SysLib.rawwrite(nextFreeBlockNum, nextFreeBlock);
					SysLib.rawwrite(blockNumber,newFreeBlock);
					return true;
				}
				// Keep iterating through free list
				nextFreeBlockNum = temp;
			}			
		}

		// Operation failed
		return false;
	}

	
	
	/**
	 * Wipes the disk clean, resets superblock to default values
	 * @param argInodeBlocks			Num inodes to make space for
	 */
	public void format(int argInodeBlocks){

		if(argInodeBlocks < 0){
			argInodeBlocks = defaultInodeBlocks;
		}
		totalInodes = argInodeBlocks;


		// Write fresh inodes to disk
		Inode tempInode = null;
		for(int i = 0; i < totalInodes; i++){
			tempInode = new Inode();
			tempInode.flag = 0;
			tempInode.toDisk((short)i);
		}
		
		// Set free list to first non-inode block
		freeList = (totalInodes / 16) + 2;

		
		// Write empty (free) blocks to disk
		byte[] freeBlock = null;
		for(int i = freeList; i < defaultBlocks - 1; i++){
			freeBlock = new byte[Disk.blockSize];
			for(int j = 0; j < Disk.blockSize; j++){
				freeBlock[j] = 0;
			}
			// Set pointer to next free block
			SysLib.int2bytes(i+1, freeBlock, 0);
			// Write free block to disk
			SysLib.rawwrite(i, freeBlock);
		}

		
		// Final free block points to -1
		freeBlock = new byte[Disk.blockSize];
		for(int j = 0; j < Disk.blockSize; j++){
			freeBlock[j] = 0;
		}
		SysLib.int2bytes(-1, freeBlock, 0);
		SysLib.rawwrite(defaultBlocks - 1, freeBlock);	

		
		// Write new superblock to disk
		byte[] newSuper = new byte[Disk.blockSize];
		SysLib.int2bytes(totalBlocks,newSuper,totalBlockLocation);
		SysLib.int2bytes(totalInodes,newSuper,totalInodeLocation);
		SysLib.int2bytes(freeList,newSuper,freeListLocation);
		SysLib.rawwrite(0,newSuper);

	}
}
