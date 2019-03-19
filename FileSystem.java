/**
 * 
 * FileSystem.java
 * 
 * George Zhou and Gahl Goziker
 * CSS 430
 * March 2019
 *
 */
public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;
    private final static boolean SUCCESS = true;
    private final static boolean FAILURE = false;

    // Start position of seek pointer
    private final int SEEK_SET = 0; // from the beginning of the file
    private final int SEEK_CUR = 1; // from the current position of the file pointer in the file
    private final int SEEK_END = 2; // from the end of the file

    public FileSystem(int diskBlocks) {
        superblock = new SuperBlock(diskBlocks); // Init new SuperBlock with default format 64

        directory = new Directory(superblock.inodeBlocks); // Root Directory init to '/'

        filetable = new FileTable(directory); // Init FileTable and put directory to it

        FileTableEntry dirEnt = open("/", "r"); // Reconstruct the fileTableEntry
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }

    /**
     * Sync the fileSystem back to the disk
     * Also calls the sync() function in the superBlock
     */
    public void sync() {
        //open root directory with write access
        FileTableEntry openRoot = open("/", "w");

        //write directory to root
        write(openRoot, directory.directory2bytes());

        //close root directory
        close(openRoot);

        //sync superblock
        superblock.sync();
    }

    /**
     * Erase all data in the fileSystem and reset it to original condition
     * @param files
     * @return
     */
    public boolean format(int files) {
        superblock.format(files); // Call format in the superBlock with

        directory = new Directory(superblock.inodeBlocks); // Create root directory

        filetable = new FileTable(directory); // Store directory in fileTAble

        return true; // Format will always be successful
    }

    /**
     * Open the file by Name by specified mode
     * If it's the write mode method will call deallocaateAllBlocks
     * @param filename
     * @param mode
     * @return
     */
    FileTableEntry open(String filename, String mode) {
        FileTableEntry newEntry = filetable.falloc(filename, mode);
        if (mode == "w") {
            if (!deallocAllBlocks(newEntry))
                return null; // deallocate all blocks
        }
        return newEntry;     // return new file table entry
    }

    /**
     * Close the open file
     * Decrement the user count each time
     * If no user accessing at the time, free up the file entry in the file table
     * @param ftEnt
     * @return
     */
    public boolean close(FileTableEntry ftEnt) {
        synchronized (ftEnt) { // Lock
            ftEnt.count--; // decrement user count
            if (ftEnt.count == 0) {
                return filetable.ffree(ftEnt); // free file entry

            }
            return true;
        }
    }

    /**
     * return file size
     * @param ftEnt
     * @return
     */
    int fsize(FileTableEntry ftEnt) {
        synchronized (ftEnt) {
            Inode tempInode = ftEnt.inode;
            return tempInode.length;
        }
    }

    /**
     * Read a file from the file table entry
     * @param ftEnt
     * @param buffer
     * @return
     */
    int read(FileTableEntry ftEnt, byte[] buffer) {
        if ((ftEnt.mode == "w") || (ftEnt.mode == "a"))
            return -1;

        int size = buffer.length;               // size of the file
        int readBuffer = 0;                     // track the read buffer
        int readError = -1;                     // if exception on read
        int blockSize = 512;
        int iterationSize = 0;                  // how much more to read

        synchronized (ftEnt) {
            while ((ftEnt.seekPtr < fsize(ftEnt) && (size > 0))) { // Loop thru data
                int target = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                if (target == readError) { // check if valid
                    break;
                }
                byte[] data = new byte[blockSize]; // read blocks of data
                SysLib.rawread(target, data);

                int dataOffset = ftEnt.seekPtr % blockSize; // pointer to read
                int blockLeft = blockSize - dataOffset; // how much left
                int fileLeft = fsize(ftEnt) - ftEnt.seekPtr; // how much file is left

                if (blockLeft < fileLeft)
                    iterationSize = blockLeft;
                else
                    iterationSize = fileLeft;

                if (iterationSize > size)
                    iterationSize = size;

                System.arraycopy(data, dataOffset, buffer, readBuffer,
                        iterationSize); // copy file read to buffer

                ftEnt.seekPtr += iterationSize; // update variable
                readBuffer += iterationSize;
                size -= iterationSize;
            }
            return readBuffer;
        }
    }

    /**
     * Write to a file
     * @param ftEnt
     * @param buffer
     * @return
     */
    int write(FileTableEntry ftEnt, byte[] buffer) {
        int bytesWritten = 0; // Bytes to write
        int bufferSize = buffer.length; // remaining size of buffer
        int blockSize = 512;

        if (ftEnt == null || ftEnt.mode == "r") { // if valid
            return -1;
        }

        synchronized (ftEnt) {
            while (bufferSize > 0) {
                int loc = ftEnt.inode.findTargetBlock(ftEnt.seekPtr); // location of block

                if (loc == -1) { // if block is full
                    short newLoc = (short) superblock.getFreeBlock(); // new free block to write to

                    int testPtr = ftEnt.inode.getIndexBlockNumber(ftEnt.seekPtr, newLoc); // index block and test pointer

                    if(testPtr == -3){ // if null pointer
                        short freeBlock = (short)this.superblock.getFreeBlock();

                        if (!ftEnt.inode.setIndexBlock(freeBlock)) { // indirect pointer is -1
                            return -1;
                        }

                        if (ftEnt.inode.getIndexBlockNumber(ftEnt.seekPtr, newLoc) != 0) { // error on block pointer
                            return -1;
                        }
                    } else if (testPtr == -2 || testPtr == -1){
                        return -1;
                    }
                    loc = newLoc;
                }
                
                byte[] tempBuffer = new byte[blockSize];    // new byte array
                SysLib.rawread(loc, tempBuffer);            // read block to array

                int tempPtr = ftEnt.seekPtr % blockSize;    // loop thru file
                int diff = blockSize - tempPtr;             // size difference

                // append to end
                if (diff > bufferSize) {
                  System.arraycopy(buffer, bytesWritten, tempBuffer, tempPtr, bufferSize);
                  SysLib.rawwrite(loc, tempBuffer);         // write block to memory

                  ftEnt.seekPtr += bufferSize;              // increment seek pointer
                  bytesWritten += bufferSize;               // increment bytes written
                  bufferSize = 0;                           // update buffer size

                // copy remaining
                } else {                                   
                   System.arraycopy(buffer, bytesWritten, tempBuffer, tempPtr, diff);
                   SysLib.rawwrite(loc, tempBuffer);        // write block to array

                   ftEnt.seekPtr += diff;                   // increment seek pointer
                   bytesWritten += diff;                    // increment bytes written
                   bufferSize -= diff;                      // decrement remaining buffer size
                }

            }
            if (ftEnt.seekPtr > ftEnt.inode.length) { // increment inode length
                ftEnt.inode.length = ftEnt.seekPtr;
            }

            ftEnt.inode.toDisk(ftEnt.iNumber);              // Save inode to disk
            return bytesWritten;
        }
    }

    /**
     * Free up all the blocks
     * @param ftEnt
     * @return
     */
    private boolean deallocAllBlocks(FileTableEntry ftEnt) {
        short notValid = -1; // can't read
        // inode is null
        if (ftEnt.inode.count != 1) {
            SysLib.cerr("Null pointer - could not deallocAllBlocks.\n");
            return false;
        }

        // direct pointer blocks
        for (short blockId = 0; blockId < ftEnt.inode.directSize; blockId++) {
            if (ftEnt.inode.direct[blockId] != notValid) {
                superblock.returnBlock(blockId);
                ftEnt.inode.direct[blockId] = notValid;
            }
        }

        // indirect ptr
        byte[] data = ftEnt.inode.freeIndirectBlock();
        // direct pointer if != null
        if (data != null) {
            short blockId;
            while ((blockId = SysLib.bytes2short(data, 0)) != notValid) {
                superblock.returnBlock(blockId);
            }
        }
        ftEnt.inode.toDisk(ftEnt.iNumber);// write back inodes to disk
        return true;
    }

    /**
     * delete a file from fileSystem by name
     * @param filename
     * @return
     */
    boolean delete(String filename) {
        FileTableEntry tcb = open(filename, "w");       // get TCB
        if (directory.ifree(tcb.iNumber) && close(tcb)) { // free up
            // delete
            return SUCCESS;                              // success
        } else {
            return FAILURE;                              // other user accessing too
        }
    }

    /**
     * Seek
     * @param ftEnt
     * @param offset
     * @param whence
     * @return
     */
    int seek(FileTableEntry ftEnt, int offset, int whence) {
        synchronized (ftEnt) {
            switch (whence) {
                // beginning of the file
                case SEEK_SET:
                    // Sets the file's seek pointer to the offset bytes from
                    // the beginning of the file
                    ftEnt.seekPtr = offset;
                    break;

                // current position of the file pointer,
                case SEEK_CUR:
                    // current value plus
                    // the offset
                    ftEnt.seekPtr += offset;
                    break;

                // from the end of the file,
                case SEEK_END:
                    // size of the file plus
                    // the offset
                    // get the size of the file by using the length form inode
                    ftEnt.seekPtr = ftEnt.inode.length + offset;
                    break;

                // failure
                default:
                    return -1;
            }

            // Reset to zero
            if (ftEnt.seekPtr < 0) {
                ftEnt.seekPtr = 0;
            }

            // End of the file
            if (ftEnt.seekPtr > ftEnt.inode.length) {
                ftEnt.seekPtr = ftEnt.inode.length;
            }

            // Success
            return ftEnt.seekPtr;
        }
    }
}

