import com.sun.jna.platform.WindowUtils;

public class Test {
	public static void main(String[] args) {
		WindowUtils.getAllWindows(true).forEach(desktopWindow -> {
			System.out.println("------------------------------");
			System.out.println(desktopWindow.getFilePath());
			System.out.println(desktopWindow.getTitle());
			System.out.println(desktopWindow.getLocAndSize());
		});
	}

}
