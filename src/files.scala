/**************************************************************************************************
Rapture I/O Library
Version 0.6.0

The primary distribution site is

  http://www.propensive.com/

Copyright 2011 Propensive Ltd.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing permissions and limitations under the
License.
***************************************************************************************************/

package rapture.io

import java.io._
import java.net._

/** Provides support for accessing the file system through FileUrls. This is a wrapper for Java's
  * file handling facilities, and provides roughly the same functionality within the general URL
  * framework. */
trait Files { this : Io =>

  /** Type class object for writing `FileUrl`s as `Output[Stream]`s */
  implicit object FileStreamCharWriter extends StreamWriter[FileUrl, Char] {
    def output(url : FileUrl, append : Boolean = false) : Output[Char] =
      new CharOutput(new BufferedWriter(new FileWriter(url.javaFile)))
  }

  /** Type class object for reading `FileUrl`s as `Input[Stream]`s */
  implicit object FileStreamCharReader extends StreamReader[FileUrl, Char] {
    def input(url : FileUrl) : Input[Char] =
      new CharInput(new BufferedReader(new FileReader(new JavaFile(url.pathString))))
  }

  /** The file scheme object used as a factory for FileUrls. */
  object File extends Scheme[FileUrl]("file") with UrlBase[FileUrl] { thisUrlBase =>
    
    /** Provides a FileUrl for the current working directory, as determined by the user.dir
      * environment variable. */
    def currentDir = makePath(System.getProperty("user.dir").split("/").filter(_ != ""))
    
    /** Method for creating a new instance of this type of URL.
      *
      * @param elements The elements of the path for the new FileUrl to create */
    def makePath(elements : Seq[String]) = new FileUrl(thisUrlBase, elements.toArray[String])
    
    /** Method for creating a new FileUrl from a java.io.File. */
    def apply(file : JavaFile) = makePath(file.getAbsolutePath.split("\\/"))
    
    /** Reference to the scheme for this type of URL */
    def scheme : Scheme[FileUrl] = File
   
    /** Creates a new FileUrl of the specified resource in the filesystem root.
      *
      * @param resource the resource beneath the filesystem root to create. */
    def /(resource : String) = makePath(Array(resource))
    
    /** Creates a new FileUrl of the specified path, relative to the filesystem root. */
    def /(path : RelativePath) = makePath(path.elements)
    
    /** Creates a new FileUrl of the specified path, on the filesystem root. */
    def /(path : AbsolutePath[FileUrl]) = makePath(path.elements)
  }

  class FileUrl(val urlBase : UrlBase[FileUrl], val elements : Seq[String]) extends Url[FileUrl]
      with PathUrl[FileUrl] {

    lazy val javaFile : JavaFile = new JavaFile(pathString)
    
    /** Returns true if the file or directory represented by this FileUrl can be read from. */
    def readable : Boolean = javaFile.canRead()
   
    /** Returns true if the file or directory represented by this FileUrl can be written to. */
    def writable : Boolean = javaFile.canWrite()
    
    /** Deletes the file represented by this FileUrl. If the recursive flag is set and the
      * filesystem object is a directory, all subfolders and their contents will also be
      * deleted. */
    def delete(recursive : Boolean = false) : Boolean =
      if(recursive) deleteRecursively() else javaFile.delete()
    
    /** Add a hook to the filesystem to delete this file upon shutdown of the JVM. */
    def deleteOnExit() : Unit = javaFile.deleteOnExit()
    
    /** Returns true if this object exists on the filesystem. */
    def exists : Boolean = javaFile.exists()
    
    /** Returns the filename of this filesystem object. */
    def filename : String = javaFile.getName()
    
    /** Returns false if the filesystem object represented by this FileUrl is a file, and true if
      * it is a directory. */
    def isDirectory : Boolean = javaFile.isDirectory()
    
    /** Returns true if the filesystem object represented by this FileUrl is a file, and false if
      * it is a directory. */
    def isFile : Boolean = javaFile.isFile()
    
    /** Returns true if the file or directory is hidden. */
    def hidden : Boolean = javaFile.isHidden()
   
    /** Returns the date of the last modification to the file or directory. */
    def lastModified = new java.util.Date(javaFile.lastModified())
    
    /** Returns the size of the file in bytes. */
    def length : Long = javaFile.length()
    
    /** Returns the size of the file in bytes. */
    def size : Long = javaFile.length()
    
    /** Returns a list of all filesystem objects immediately beneath this FileUrl if it represents
      * a directory, or Nil if it represents a file. */
    def children : List[FileUrl] =
      if(isFile) Nil else javaFile.list().toList map { fn : String => /(fn) }
    
    /** If this FileUrl represents a directory, returns an iterator over all its descendants,
      * otherwise returns the empty iterator. */
    def descendants : Iterator[FileUrl] = children.iterator flatMap { c =>
      if(c.isDirectory) Iterator(c) ++ c.descendants else Iterator(c)
    }
    
    /** Creates a new instance of this type of URL. */
    def makePath(xs : Seq[String]) : FileUrl = File.makePath(xs)
    
    /** If the filesystem object represented by this FileUrl does not exist, it is created as a
      * directory, provided that either the immediate parent directory already exists, or the
      * makeParents path is set. */
    def mkdir(makeParents : Boolean = false) : Boolean =
      if(makeParents) javaFile.mkdirs() else javaFile.mkdir()
    
    /** Renames this file to a new location. */
    def renameTo(dest : FileUrl) : Boolean = javaFile.renameTo(dest.javaFile)
    
    /** Copies this file to a new location specified by the dest parameter. */
    def copyTo(dest : FileUrl)(implicit sr : StreamReader[FileUrl, Byte],
        sw : StreamWriter[FileUrl, Byte]) : Boolean = sr.pump(this, dest) > 0
    
    /** Moves this file to a new location specified by the dest parameter. This will first attempt
      * to move the file by renaming it, but will attempt copying and deletion if renaming fails. */
    def moveTo(dest : FileUrl) = renameTo(dest) || copyTo(dest) && delete()
   
    /** Set the last modified time of this file or directory. */
    def lastModified_=(d : java.util.Date) = javaFile.setLastModified(d.getTime)
    
    /** Extract the file extension from the name of this file. */
    def extension : Option[String] =
      if(filename contains ".") Some(filename.split("\\.").last) else None
    
    /** Attempt to alter the permissions of this file so that it is writable. */
    def writable_=(b : Boolean) =
      if(!b) javaFile.setReadOnly() else writable || (throw new IOException("Can't set writable"))
    
    /** Creates a temporary file beneath this directory with the prefix and suffix specified. */
    def tempFile(prefix : String = "tmp", suffix : String = "") =
      File(java.io.File.createTempFile(prefix, suffix, javaFile))
    
    private def deleteRecursively() : Boolean = {
      if(isDirectory) children.foreach(_.deleteRecursively())
      delete()
    }
  }
}
