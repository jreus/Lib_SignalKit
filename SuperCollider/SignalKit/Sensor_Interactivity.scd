


// Standard Dev of a Sensor Stream

s.boot;

a = Bus.control(s,1);

Ndef( \stddev, {
	SendReply.kr( Impulse.kr(20), '/stddev', StdDevUGen.kr( In.kr( \bus.kr(a.index), 1 ), \numsamples.kr(300) ), 1 )
});

OSCdef( \stddev, { |msg| msg.postln; }, "/stddev" );

Ndef( \mean, {
	SendReply.kr( Impulse.kr(20), '/mean', RunningSum.kr( In.kr( \bus.kr(a.index), 1 ), \numsamples.kr(300) ) / \numsamples.kr(300), 1 ) } );

OSCdef( \mean, { |msg| msg.postln; }, "/mean" );


Ndef(\stddev).play;
Ndef(\mean).play;

a.set( 1.1);



// In language...

[1,2,3,4,5,6].stdDev;
[1,2,3,4,5,6].mean;

// Just need an array that behaves like a queue..

c = ();
c.qsize = 5;
c.q = Array.fill(c.qsize, {0});
c.setSize_({|newsize|
	c.qsize = newsize;
	("Newsize: "+newsize).postln;
});
c.nq = {|item|
	if(c.q.size < c.qsize) {
		// queue is not full
		c.q = c.q.add(item);
	}{
		// queue is full, put item at the end
		c.q = c.q.rotate(-1).put(c.q.size - 1);
	};
}

// How to call functions again when prototyping like this?

c.setSize(2)
c.array

Array.newClear(10)

	a.dmx.setVal_({|e,chan, value| a.dmx.currCue.put(chan, value); });
