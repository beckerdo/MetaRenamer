package info.danbecker.metarenamer;

import java.util.Map;
import java.util.Set;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

public class MediaTypeExample {

	public static void describeMediaType() {
		Class<?> instClass = new Object(){}.getClass();
		System.out.println( instClass.getEnclosingClass().getSimpleName() + "." + instClass.getEnclosingMethod().getName() );

		MediaType type = MediaType.parse("text/plain; charset=UTF-8");

		System.out.println("   type:    " + type.getType());
		System.out.println("   subtype: " + type.getSubtype());

		Map<String, String> parameters = type.getParameters();
		System.out.println("   parameters:");
		for (String name : parameters.keySet()) {
			System.out.println("     " + name + "=" + parameters.get(name));
		}
		// <end id="media_type"/>
	}

	public static void listAllTypes() {
		Class<?> instClass = new Object(){}.getClass();
		System.out.println( instClass.getEnclosingClass().getSimpleName() + "." + instClass.getEnclosingMethod().getName() );

		MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();

		for (MediaType type : registry.getTypes()) {
			Set<MediaType> aliases = registry.getAliases(type);
			if ( (null != aliases) && (aliases.size() > 0))
			   System.out.println("   " + type + ", also known as " + aliases);
			else
			   System.out.println("   " + type);
		}
	}

	public static void main(String[] args) throws Exception {
		MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();

		listAllTypes();
		
		describeMediaType();

		Class<?> instClass = new Object(){}.getClass();
		System.out.println( instClass.getEnclosingClass().getSimpleName() + "." + instClass.getEnclosingMethod().getName() );
		MediaType type = MediaType.parse("image/svg+xml");
		while (type != null) {
			System.out.println("   " + type);
			type = registry.getSupertype(type);
		}
		
	}

}
