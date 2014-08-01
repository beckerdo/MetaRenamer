package info.danbecker.metarenamer;

public enum FileAttribute {	
	DIRECTORY( "directory" ),
	FILE( "file" ),
	EXISTS( "exists" ),
	READABLE( "readable" ),
	WRITABLE( "writeable" ),
	EXECUTABLE( "executable" ),
	HIDDEN( "hidden" ),
	LINK( "link" );
	
	private FileAttribute(String name ) {
    	this.name =  name;
    }
	
	public String getName() {
		return name;
	}
	public String toString() { return getName(); }
	
	protected String name;		
}