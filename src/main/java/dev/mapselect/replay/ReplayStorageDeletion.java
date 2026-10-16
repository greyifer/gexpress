package dev.mapselect.replay;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
/** Only delete the replay directory owned by this world. Do not follow links. */
public final class ReplayStorageDeletion {
    private ReplayStorageDeletion(){}
    public static void purge(Path saveRoot,Path replayRoot)throws IOException{
        Path expected=saveRoot.toAbsolutePath().normalize().resolve("gexpress-replays");
        Path root=replayRoot.toAbsolutePath().normalize();
        if(!root.equals(expected))throw new IOException("Unexpected replay deletion path: "+root);
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return;
        if(Files.isSymbolicLink(root)||!root.toRealPath().equals(saveRoot.toRealPath().resolve("gexpress-replays")))throw new IOException("Replay directory is redirected; refusing deletion");
        Path physical=root.toRealPath();
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs)throws IOException{
                if(!dir.toRealPath().startsWith(physical))throw new IOException("Replay directory points outside storage");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException{Files.delete(file);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path dir,IOException error)throws IOException{
                if(error!=null)throw error;if(!dir.equals(root))Files.delete(dir);return FileVisitResult.CONTINUE;
            }
        });
    }
}
