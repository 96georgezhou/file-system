/**
 * 
 * FileTable.java
 * 
 * George Zhou and Gahl Goziker
 * CSS 430
 * March 2019
 *
 */
import java.util.Vector;

public class FileTable {
    private Vector<FileTableEntry> table;   // file table vector
    private Directory dir;                  // the root
    public final static int UNUSED = 0;     // file does not exist
    public final static int USED = 1;       // file exists but is not R or W by anyone
    public final static int READ = 2;       // file is read by someone
    public final static int WRITE = 3;      // file is written by someone

    /**
     * Constructor
     * @param directory
     */
    public FileTable(Directory directory) { 
        // init a filetable
        table = new Vector<FileTableEntry>(); 
        // reference directory from fileSystem
        dir = directory;           
    }

    /**
     * falloc
     * @param filename
     * @param mode
     * @return
     */
    public synchronized FileTableEntry falloc(String filename, String mode) {
        short iNumber = -1; // inode
        Inode inode = null; // inode

        while (true) {
            iNumber = (filename.equals("/") ? (short) 0 : dir.namei(filename)); // get inumber

            // if number exits
            if (iNumber >= 0) {
                inode = new Inode(iNumber);

                // reading mode
                if (mode.equals("r")) {
                    
                    // check if used or not
                    if (inode.flag == READ 
                        || inode.flag == USED 
                        || inode.flag == UNUSED) {

                        // change flag to read
                        inode.flag = READ;
                        break;
                    
                    // Wait the writer to finish
                    } else if (inode.flag == WRITE) {
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }

                // File is requested for writing
                } else {
                    
                    // Change to write
                    if (inode.flag == USED || inode.flag == UNUSED) {
                        inode.flag = WRITE;
                        break;
                    
                    // wait till reader or writer to finish
                    } else {
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }
                }

            // file does not exits
            } else if (!mode.equals("r")) {
                iNumber = dir.ialloc(filename);
                inode = new Inode(iNumber);
                inode.flag = WRITE;
                break;

            } else {
                return null;
            }
        }

        inode.count++;  // increment user count
        inode.toDisk(iNumber);
        // new fte and add to file table
        FileTableEntry entry = new FileTableEntry(inode, iNumber, mode);
        table.addElement(entry);
        return entry;
    }

    /**
     * Free a file table entry
     * @param entry
     * @return
     */
    public synchronized boolean ffree(FileTableEntry entry) {
        Inode inode = new Inode(entry.iNumber);
        // check if in table
        if (table.remove(entry)) {
            if (inode.flag == READ) {
                // set the flag to used
                if (inode.count == 1) {
                    notify();
                    inode.flag = USED;
                }

            } else if (inode.flag == WRITE) {
                // set the flag to used
                inode.flag = USED;
                // wake up all threads
                notifyAll();
            }
                // Decrement the number of users
            inode.count--;
            inode.toDisk(entry.iNumber);
            return true;
        }
        return false;
    }

    /**
     * Being called on format
     * @return
     */
    public synchronized boolean fempty() {
        return table.isEmpty();  // return if empty
    }                            
}
