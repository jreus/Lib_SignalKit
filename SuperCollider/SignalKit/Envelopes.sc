

// Some useful Envelopes
+ Env {

	// fixed duration envelopes

	*square {|dur=1.0, level=1.0, curve=\lin|
		var attk = 0.05;
		if(attk > (dur * 0.05)) {
			attk = dur * 0.05;
		};
		dur = dur - (2*attk);

		^this.new([0, level, level, 0],[attk, dur, attk],curve);
	}


}
