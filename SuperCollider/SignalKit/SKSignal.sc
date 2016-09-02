/*****************************************

    SignalKit
    Jonathan Reus-Brodsky

    Toolkit for working with control signal data.


****************************************/


// TODO:
//  SKSignal add commonly useful statistical analyses like std deviation, variance, etc..
//  as well as being able to back up a SKSignalView view (TODO)
/*
u = SKSignal.new(5);
u.standardDev
u.runningAvg
etc...
*/


/*



*/


/*************
SKDataPoint
A single, 1-dimensional datapoint
*************/
SKDataPoint {
	var <timeStamp, <data;

	*new {|val, timestamp=nil|
		^super.new.initme(val, timestamp)
	}

	initme {|val, ts|
		if (ts == nil) {
			timeStamp = Process.elapsedTime;
		} {
			timeStamp = ts;
		};
		data = val;
	}

	asString {
		var stringrep;
		stringrep = "("++data++","++timeStamp++"s)";
		^stringrep;
	}

	asJSON {
		var json;
		json = "["++data++","++timeStamp++"]";
		^json;
	}
}


/*************
SKSignal
1-dimensional Signal datatype.
Increases size as needed.
*************/
SKSignal {
	var <data, <startTime;

	*new {|starttime=nil|
		^super.new.initme(starttime);
	}

	initme {|starttime|
		data = List.new(0);
		if(starttime.isNil) {
			startTime = Process.elapsedTime;
		} {
			startTime = starttime;
		}
	}

	add {|datapoint|
		data.add(SKDataPoint.new(datapoint))
	}

	// get the last added value, or go steps values back from the top
	lastVal {|steps=0|
		^(data.at(data.size - 1 - steps));
	}

	size {
		^data.size;
	}

	// Get the avg over the last numvals values
	avg {|numvals|
		var result, sum=0;
		numvals.do {|i|
			sum = sum + data.at(data.size - 1 - i);
		};
		^(sum / numvals);
	}

	at {|idx|
		var maxidx = data.size - 1;
		if((idx > maxidx) || (idx < 0)) {
			Error("Index out of range in SKSignal.at - index: " + idx).throw;
		};
		^data.at(idx)
	}

	asJSON {|id|
		var json, first=true;
		json = "{\"sig:\":"++id++",\"startTime\":"++startTime++",\"data\":[";
		data.do {|datapoint, i|
			if(first == true) {
				first = false;
			} {
				json = json ++ ",";
			};
			json = json ++ datapoint.asJSON();
		};
		json = json++"]}";
		^json;
	}

}


/*************
SKSignalCircular
Signal using a circular buffer of fixed size.
Can be faster than SKSignal

::NOTE:: ... not really up to date with the rest of the SignalKit architecture ...
*************/
SKSignalCircular {
	var data, datasize, top;

	*new {|history_depth=1000|
		^super.new.initme(history_depth);
	}

	initme {|history_depth|
		datasize = history_depth;
		data = 0.0.dup(history_depth);
		top = -1;
	}

	add {|datapoint|
		top = top + 1;
		if(top >= data.size) {
			top = 0;
		};
		data[top] = datapoint;
	}

	lastVal {|steps=0|
		^(data.wrapAt(top - steps));
	}

	size {
		^datasize;
	}

	// Get the avg over the last numvals values
	avg {|numvals|
		var result, sum=0;
		numvals.do {|i|
			sum = sum + data.wrapAt(top - i);
		};
		^(sum / numvals);
	}

	at {|i|
		var idx, maxidx = data.size - 1;
		if((i > maxidx) || (i < 0)) {
			Error("Index out of range in SKSignal.at - index: " + idx).throw;
		};
		idx = top - maxidx + i;
		^data.wrapAt(idx)
	}

	put {|i, datapoint|
		var idx, maxidx = data.size - 1;
		if((i > maxidx) || (i < 0)) {
			Error("Index out of range in SKSignal.put - index: "+idx).throw;
		};
		idx = top - maxidx + i;
		data.wrapPut(idx, datapoint)
	}

	asString {
		^data.rotate(-1 * (top + 1));
	}
}

/*******
SKMultiSignal

Multiple signal streams in a single data structure.

********/
SKMultiSignal {
	var <signals, <startTime;

	*new {|numsignals|
		^super.new.initme(numsignals)
	}

	initme {|numsignals|
		startTime = Process.elapsedTime;
		signals = {SKSignal.new(starttime: startTime)}.dup(numsignals);
	}

	at {|signal_idx|
		^signals[signal_idx];
	}

	add {|signal_idx, datapoint|
		signals[signal_idx].add(datapoint);
	}

	saveDialog {
		// Open file dialog
		Dialog.savePanel({|fpath|
			this.save(fpath);
		}, {
			"Save Operation Cancelled".postln;
		});
	}

	asJSON {
		var json, first=true;
		json = "{\"numsignals\":"++signals.size++",\"signals\":[\n";
		signals.do {|sig, i|
			if(first == true) {
				first = false;
			} {
				json = json ++ ",\n";
			};
			json = json ++ sig.asJSON(i);
		};
		json = json++"]}";
		^json;
	}

	save {|filepath=nil|
		if(filepath.isNil) {
			filepath = JUtil.pwd +/+ "signal_" ++ Date.getDate.stamp ++ ".sig";
		};

		if(File.exists(filepath)) {
			Post << "WARNING: Overwriting file " ++ filepath << $\n;
		};

		// Save to filepath
		Post << "Saving data as " << filepath << $\n;
		File.use(filepath, "w", {|fp|
			var i;
			// Write the file!!
			fp.write(this.asJSON());
		});
		^filepath;
	}

}



/***********
SK_EDA - Electrodermal Activity Signal Processing.

Uses a SKSignal to do record & process Electrodermal Activity.

Calculate peaks (callback on peak enter & peak exit)
Count peaks over a given window of time.

***********/
SK_EDA {
	var <signal, <peaktimes;
	var <peak, <trough, <thresh, <amp, <peak_count;
	var time_lastpeak;
	classvar inter_peak_wait = 1, min_peak_time = 0.5; // defines peak detection windows
	var <>peakOnAction, <>peakOffAction, <>newDataAction; // callbacks

	*new {
		^super.new.init(1000);
	}

	init {|datapoints|
		signal = SKSignal.new(datapoints);
		peaktimes = SKSignal.new(datapoints);
		peak = false;
		peak_count = 0;
		time_lastpeak = Process.elapsedTime;
		peakOnAction = {|val| "EDA Peak Detected".postln; };
		peakOffAction = {|val| "EDA Peak Ending".postln;};
		newDataAction = {|val| };
	}

	/**
	  add - Adds a new datapoint to the signal.
	**/
	add {|datapoint|
		var currenttime, elapsedsince, lastval;
		currenttime = Process.elapsedTime;
		elapsedsince = currenttime - time_lastpeak;
		lastval = signal.lastVal();
		signal.add(datapoint);
		newDataAction.value(datapoint);

		if((datapoint > lastval) && (peak == false) && (elapsedsince > inter_peak_wait)) {
	    // We hit a peak
		peak = true;
		peak_count = peak_count + 1;
		time_lastpeak = currenttime;
		peaktimes.add(currenttime);
		// Do the peak callback
		peakOnAction.value(datapoint);
	};

	if((datapoint < lastval) && (peak == true) && (elapsedsince > min_peak_time)) {
		// Exit peak
		peak = false;
		peakOffAction.value(datapoint);
	};

	}

}

/***********
SK_ECG - ECG Signal Processing

Uses a SKSignal to do record & process ECG activity.

Calculates beats (callback on each beat)
BPM
Heart Rate Variation
Pulse Amplitude

***********/
SK_ECG {
	var <ibi, <lastBeatTime, <peak, <trough, <thresh, <amp, <bpm;
	var <firstBeat, <secondBeat;
	var <pulse;
	var <signal, <ibivals;
	var <>beatOnAction, <>beatOffAction; // callbacks

	*new {
		^super.new.init(1000);
	}

	init {|datapoints|
		signal = SKSignal.new(datapoints);
		ibivals = SKSignal.new(50);
		ibi = 0.1;
		pulse = false;
		lastBeatTime = Process.elapsedTime;
		peak = 0.5; trough = 0.5; thresh = 0.501;
		firstBeat=true; secondBeat=false;
		beatOnAction = {|val| "Blood Pulse Detected".postln;};
		beatOffAction = {|val| "Blood Pulse Exit".postln;};

	}

	add {|datapoint|
		var currentTime, runningTotal = 0, timeElapsed;
		var ibi_thresh = (ibi / 5.0) * 3.0, avg_ibi = 0;
		signal.add(datapoint);

		// Get current time & time elapsed since last detected ecg beat
		currentTime = Process.elapsedTime; // get the latest time in ms
		timeElapsed = currentTime - lastBeatTime;  // monitor the time in us since the last beat

		// Track the peak
		if((datapoint > thresh) && (datapoint > peak)) { // thresh is used for debouncing
			peak = datapoint;
		};

		if((datapoint < thresh) && (timeElapsed > ibi_thresh) && (datapoint < trough)) {
			trough = datapoint;
		};

		// Look for a heart beat, during every cardiac pulse wave the signal surges upwards
		// from the average. Only check if it's been at least 300 ms since the last beat to avoid noise
		if(timeElapsed > 0.300) {
			if((datapoint > thresh) && (pulse == false) && (timeElapsed > ibi_thresh)) {
				pulse = true;
				ibi = currentTime - lastBeatTime;
				lastBeatTime = currentTime;
				beatOnAction.value(datapoint);

				if(secondBeat) {
					secondBeat = false;
					"Second beat...".postln;
					// at the second beat we have a single IBI value that can be used to
					// get a realistic BPM & HRV
					20.do {
						ibivals.add(ibi);
					};
				};

				if(firstBeat) {
					firstBeat = false;
					secondBeat = true;
					"First beat...".postln;
					^0; // ibi value is worthless, at this point we just track the beat
				};

				// Get the avg of the last 20 ibi values
				ibivals.add(ibi);
				avg_ibi = ibivals.avg(20); // get the avg of the last 20 ibi values
				bpm = 60.0 / avg_ibi;
				// how many ibi's in 60s? This gives us the average BPM
       };
    }; // end, beat check

    // When the signal starts going down below the threshhold, the beat is over
		if((datapoint < thresh) && (pulse == true)) {
      pulse = false;                  // reset the pulse flag, the pule is over
      amp = peak - trough;            // calculate the amplitude of the last pulse
			beatOffAction.value(datapoint);
      thresh = (amp / 2) + trough;    // calculate a new threshhold value for detecting the next pulse
      peak = thresh;                  // reset the peak and trough values for detecting the amplitude of the next pulse
      trough = thresh;
    };

    // If 2.5s go by without a beat, reset the system...
    if(timeElapsed > 2.5) {
      thresh = 0.501;
      peak = 0.5;
      trough = 0.5;
      lastBeatTime = currentTime;
      firstBeat = true;
      secondBeat = false;
    };
	}
}



