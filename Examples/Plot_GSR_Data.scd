/*******************************************

Plots GSR data.
Demonstrates using QuickSerial to communicate with Arduino also using QuickSerial lib

Jonathan Reus-Brodsky

**************************************/


/*********************************************
USING QUICKSERIAL with Arduino Pro Mini
**********************************************/

// Set up Serial Connection
(
~serial = QuickSerial.new();

~plots = DynamicMultiPlot.new("SerialData", 800, 700, 400, 800, [["Raw GSR", 250, 0, 4095],["GSR DCcoupled", 250, 1024, 3*1024], ["GSR Smooth", 250, 0, 4095]], 1, margin: 10, flow: 'left');

~serial.addResponder($G, {|val|
		~plots.add(0, val);
});
~serial.addResponder($S, {|val|
		~plots.add(1, val);
});
~serial.addResponder($R, {|val|
		~plots.add(2, val);
});
)

// Start the whole thing
~serial.startSerial(115200);

// Close the serial connection so that you can upload a new sketch to the Arduino board
~serial.stopSerial;
