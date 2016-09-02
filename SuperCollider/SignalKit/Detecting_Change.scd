// HPZ2 is useful to detect changes in a signal:
// see also HPZ1
(
{
    var changingSignal = LFNoise0.ar(1000);
    var hpz1 = HPZ2.ar(changingSignal);
    [hpz1, hpz1 > 0]
}.plot
);


s = Server.quickBoot;


// HPZ1 works great on binary changes
// HPZ2... ?
( // Why does this work?
{
	var sig = MouseX.kr(-1,1,lag:0);
	var hpz2 = HPZ2.kr(sig);
	var trig = hpz2 > 0;
	SendReply.kr(trig, '/reply', [sig]);
	SinOsc.ar * EnvGen.ar(Env.perc(0.01,0.1), trig);
}.play;
)


( // But this not?
{
	var sig = SinOsc.kr(0.5);
	var hpz2 = HPZ2.kr(sig);
	var trig = hpz2 > 0;
	SendReply.kr(trig, '/reply', [sig]);
	SinOsc.ar * EnvGen.ar(Env.perc(0.01,0.1), trig);
}.play;
)


o = OSCresponderNode(nil, '/reply', {|t,r,msg|
	msg.postln;
}).add;