package kilim.examples;

import kilim.Pausable;

public class MyService extends StandardService {

	@Override
	public void doService(String message) throws Pausable {
		System.out.println(message);
	}

}
