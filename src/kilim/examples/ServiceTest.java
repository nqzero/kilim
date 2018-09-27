package kilim.examples;

import kilim.Task;

public class ServiceTest {

	public static void main(String[] args) {
		
		Task task = new Task() {
			public void execute() throws kilim.Pausable ,Exception {
				StandardService service = new MyService();
				service.service("aaaaaa");
			};
		};
		
		task.start();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		System.exit(0);
	}
}
