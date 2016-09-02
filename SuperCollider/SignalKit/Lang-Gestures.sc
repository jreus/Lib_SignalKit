/*

Jonathan Reus-Brodsky

13 March 2016

Language Gestures Library

Be able to quickly execute a number of time-based gestures in the language.
*/

/*
A wrapper class to wrap about specific objects & code so they can be controlled by gestures.

Usage:
~somespecificobject = DMX.new; // or otherwise
~generalreceiver = WrappedReceiver.new(~somespecificobject);
~generalreceiver.register_handlers('setval',{|rec, val| rec.sendDMX(val,val,val,val)});
Gesture.env(Env.perc(1,rrand(1,3)), ~generalreceiver, rrand(1.0, 4.0), del: 0.01);
*/
WrappedReceiver {
	var <receiver, r_handlers;

	*new {|thereceiver|
		^super.new.init(thereceiver);
	}

	init {|thereceiver|
		receiver = thereceiver;
		r_handlers = ();
	}

	register_handlers {|selector, cb_func|
		r_handlers.put(selector, cb_func);
	}

	// Catch-all for undefined methods
	doesNotUnderstand {|selector...args|
		if(r_handlers[selector].isNil) {
			("Receiver does not have a message handler for '" ++ selector ++ "'").postln;
		};
		r_handlers[selector].value(receiver, *args);
	}
}

/*
Gesture class for executing time-based gestures in the language.
*/
Gesture {

	/*
	  Use an envelope to define a one-dimensional gesture.
	  The receiver must respond to a method setval to be controlled by the gesture.
	*/
	*env {|env, receiver, dur, del=0.05|
		var envstr, iterations;
		if(dur != env.duration) {
			env.duration = dur;
		};
		iterations = (dur / del).ceil;
		envstr = env.asStream;
		{
			iterations.do {
				receiver.setval(envstr.next());
				del.wait;
			};
		}.fork;
	}

}
