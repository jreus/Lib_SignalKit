

// Parent class of Gestures
//   Gestures are signal classifiers that trigger a callback function when a gesture is detected.
SKGesture {
	var <signal;
	var action;
	var last_trig, <>debounce_time; // debounce variables


	*new {|history_depth=1000|
		^super.new.initme(history_depth);
	}

	initme {|history_depth|
		signal = SKSignal.new(history_depth);
		action = {
			Post << "Gesture detected for: " << this << Char.nl;
		};
		debounce_time = 0.1; // in seconds
		last_trig = Process.elapsedTime;
	}

	// Basic add data point to history. Child classes should override this function
	// to provide signal analysis when each new point is added. This function can be called to
	// perform the adding of the datapoint.
	add {|datapoint|
		signal.add(datapoint);
	}

	addAction {|actionfunc|
		action = actionfunc;
	}

	lastVal {
		^signal.lastVal;
	}

	// Returns true if the debounce timer is running and gesture recognition analysis
	// should be inactive.
	debounce_blocking {
		^((Process.elapsedTime - last_trig) < debounce_time);
	}

	// Fires the callback. Used by child classes when a gesture is recognized.
	// This method will not handle your debouncing. Use debounce_blocking to check
	// whether it's ok to fire.
	fire {
			action.value(this); // trigger the callback function
			last_trig = Process.elapsedTime;
	}

}



// Detects a peak. A rise over a threshhold followed by a fall below it over a certain number
// of minimum datapoints (the peak width).
SKGesturePeak : SKGesture {

	var <>peakwidth, <>peakthresh, windowsize;

	*new {|peakwidth, peakthresh, history_depth|
		^super.new(history_depth).init(peakwidth, peakthresh);
	}

	init {|swidth, sthresh|
		peakwidth = swidth;
		peakthresh = sthresh;
		windowsize = peakwidth * 2;
	}


	// Add datapoint, if a peak has occurred, call the action callback function.
	add {|datapoint|
		var startwindow, endwindow, cnt_high, peak_found, debounce_del, now;

		// Add new data
		super.add(datapoint);

		if (this.debounce_blocking.not) {
			// Analysis of previous data
			endwindow = signal.size - 1;
			startwindow = endwindow - windowsize;
			cnt_high = 0; peak_found = false;
			if (startwindow < 0) { startwindow = 0 };

			for(startwindow, endwindow, {|i|
				if(signal.at(i) > peakthresh) {
					cnt_high = cnt_high + 1;
				} {
					if(cnt_high >= peakwidth) {
						peak_found = true;
					} {
						// Otherwise reset count
						cnt_high = 0;
					};
				};
			});

			// A peak has occurred when the data spikes upwards above the shock threshhold and then below again
			// over the course of peakwidth # datapoints
			if (peak_found) {
				this.fire;
			};
		};
	}
}

// For backwards compatibility... SKGesturePeak used to be called SKGestureShock
SKGestureShock : SKGesturePeak {
}


// Detects a threshhold crossing.
SKGestureThresh : SKGesture {
	var <>threshhold, windowsize;

	*new {|thresh, history_depth|
		^super.new(history_depth).init(thresh);
	}

	init {|thresh|
		threshhold = thresh;
	}

	// Add datapoint, if a threshhold crossing has occurred, call the action callback function.
	add {|datapoint|
		var startwindow, endwindow, crossing_found, sigtop;

		// Add new data
		super.add(datapoint);

		if (this.debounce_blocking.not) {
			crossing_found = false;
			sigtop = signal.size - 1;
			if(sigtop > 0) {
				crossing_found = (signal.at(sigtop) > threshhold) && (signal.at(sigtop - 1) <= threshhold);
			};

			if (crossing_found) {
				this.fire;
			};
		};
	}
}



// Use to detect a threshhold crossing with histeresis (similar to a Schmidt Trigger).
SKSchmidt {
	var <>u_thresh, <>l_thresh, cb_func, state;

	*new {|uthresh, lthresh|
		^super.new.init(uthresh, lthresh);
	}

	init {|uthresh, lthresh, dbtime|
		u_thresh = uthresh;
		l_thresh = lthresh;
		cb_func = {|event| (this.class.asString + event).postln; };
		state = 'LOW';
	}

	// Handler function takes (event)
	// event = 'HIGH' | 'LOW'  <-- which thresh was crossed
	addhandler {|thefunc|
		cb_func = thefunc;
	}

	// A new data point coming in...
	addpoint {|theval|
		var inrange;

		if(state == 'LOW') {
			if(theval > u_thresh) {
				state = 'HIGH';
				cb_func.value(state);
			};
		} {
			if(theval < l_thresh) {
				state = 'LOW';
				cb_func.value(state);
			};
		};
	}
}


// Use to detect a threshhold crossing with dbounce time.
SKThresh {
	var <>thresh, <>db_time, cb_func, last_cross, state;

	*new {|thethresh=0.5, dbtime=0.1|
		^super.new.init(thethresh, dbtime);
	}

	init {|thethresh, dbtime|
		thresh = thethresh;
		db_time = dbtime;
		cb_func = {|event| (this.class.asString + event).postln; };
		last_cross = Process.elapsedTime;
		state = 'LOW';
	}

	// Handler function takes (event)
	// event = 'HIGH' | 'LOW'  <-- crossed above or below the threshhold
	addhandler {|thefunc|
		cb_func = thefunc;
	}

	addpoint {|theval|
		var db_clear, nu = Process.elapsedTime;
		db_clear = ((nu - last_cross) > db_time);
		if(db_clear) {
			if((state == 'LOW') && (theval > thresh)) {
				state = 'HIGH';
				cb_func.value(state);
				last_cross = nu;
			};
			if((state == 'HIGH') && (theval < thresh)) {
				state = 'LOW';
				cb_func.value(state);
				last_cross = nu;
			};
		};
	}
}


