package kilim.examples;

import kilim.Pausable;

public abstract class StandardService implements Service {

	@Override
	public void service(String message) throws Pausable {
		doService(message);
	}
	
	
	
	
	public abstract void doService(String message) throws Pausable;

}
