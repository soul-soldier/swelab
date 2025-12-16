package artcreator.statemachine.port;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface State {

	boolean isSubStateOf(State state);
	boolean isSuperStateOf(State state);

	public enum S implements State {
		// Root State
		CreateTemplate,

		// Concrete States
		NoImage(CreateTemplate),
		ImageLoaded(CreateTemplate),
		Processing(CreateTemplate),
		TemplateReady(CreateTemplate);

		private List<State> subStates;

		// Initial state is NoImage
		public static final S INITIAL_STATE = NoImage;

		private S(State... subS) {
			this.subStates = new ArrayList<>(Arrays.asList(subS));
		}

		@Override
		public boolean isSuperStateOf(State s) {
			boolean result = (s == null) || (this == s);
			for (State state : this.subStates)
				result |= state.isSuperStateOf(s);
			return result;
		}

		@Override
		public boolean isSubStateOf(State state) {
			return (state != null) && state.isSuperStateOf(this);
		}
	}
}