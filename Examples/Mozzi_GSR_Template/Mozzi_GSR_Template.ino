/********************************************************

  Template for working with GSR data and Mozzi.

  Uses a combination of Mozzi, SignalKit and QuickSerial
  Also triggers the LED according to a basic threshhold.
  
  Jonathan Reus-Brodsky, August 2016
  
**********************************************************/

#include <MozziGuts.h>
#include <Sample.h>
#include <a_R.h>
#include <SKgsr.h>
#include <QuickSerial.h>

// Mozzi extras
#include <IntMap.h> // a faster version of Arduino's map
#include <Metronome.h>
#include <DCfilter.h> // a DC filter

// Mozzi Config
#define AUDIO_MODE STANDARD
#define AUDIO_RATE 16384
#define USE_AREF_EXTERNAL false
#define CONTROL_RATE 64 // powers of 2 please, 128 is better/faster than usual to help smooth CONTROL_RATE adsr interpolation

// Serial config
#define SEND_SERIAL 25 // comment this line to disable serial feedback, number is the serial tick rate in ms
#define SERIAL_BAUD 115200 // standard for Mozzi

#define GSRPIN A0
#define LEDPIN 8
#define LED_SIG_THRESH 2000

#ifdef SEND_SERIAL
QuickSerial serial;
Metronome serialTimer; // used for sending serial data @ a reasonable frame rate
byte serialCounter = 0;
#endif

// Timing
Metronome metro;
byte stepCounter = 0;
uint16_t metrorate_ms = 250; // global metronome rate

// Audio Synthesis
Sample <a_R_NUM_CELLS, AUDIO_RATE> aSample(a_R_DATA);
byte gain = 128; // global gain

// Data cooking
#define SMOOTHNESS 0.7 // 0.0 no smoothing, 1.0 lots of smoothing
#define DC_FILTER_TIME 0.95 // 0.0 extreme filtering, 1.0 light/no filtering
#define DC_SIG_CENTER 2048
SKgsr gsr(GSRPIN, SMOOTHNESS);
DCfilter dcGSR(DC_FILTER_TIME);
IntMap map_gsr_gain(0, 4095, 20, 50); 
IntMap map_gsr_pitch(0, 4095, 1, 40); 


void setup(){
#ifdef SEND_SERIAL
  serial.init(SERIAL_BAUD);
  delay(100); // don't use delay after starting Mozzi
  serial.println("Startup");
  serialTimer.start(SEND_SERIAL);
#endif

  pinMode(LEDPIN, OUTPUT);
  delay(100);
  // Hello world
  for(int i = 0; i < 10; i++) {
    digitalWrite(LEDPIN, HIGH);
    delay(100);
    digitalWrite(LEDPIN, LOW);
    delay(50);
  }
  startMozzi(CONTROL_RATE); // start the sound engine
  metro.start(metrorate_ms);

  // play at the speed it was recorded
  aSample.setFreq((float) a_R_SAMPLERATE / (float) a_R_NUM_CELLS); 
}


// MOZZI: interrupt routine every CONTROL_RATE times per second
void updateControl(){
  gsr.readSample();
  
#ifdef SEND_SERIAL
  if(serialTimer.ready()) {
    reportSerial();
    serialCounter++;
  };
#endif

  doControl();
  doLEDFeedback();
}

// MOZZI: update the audio frame here, audio rate interrupt handler
int updateAudio(){
  int sig1, sig2;
  sig1 = (int)aSample.next();
  //sig2 = (int)aSample2.next();
  //return ((sig1 + sig2) * gain) >> 8;
  return sig1 * gain;
}

// Custom control data & parameter manipulations go here
void doControl() {
  uint16_t gsrval;
  gsrval = dcGSR.next(gsr.oversampled);
  if(metro.ready()){
    if(gsrval > (DC_SIG_CENTER + LED_SIG_THRESH) && (!aSample.isPlaying())) {
      aSample.start();
    }
    stepCounter++;
  }
  
  gain = map_gsr_gain(gsr.smoothed);
  //playspeed = map_gsr_pitch(gsr.smoothed) / 128.0F; 
  //metrorate_ms = map_gsr_metro(gsr.smoothed);
}

// Serial data reporting goes here
void reportSerial() {
    switch(serialCounter % 2) 
    {
      case 0: serial.write('G', gsr.oversampled); // raw gsr
      break;
      case 1: serial.write('S', gsr.smoothed); // smoothed
      break;
    };
}

// Led feedback conditions go here
void doLEDFeedback() {
  // Led feedback condition
  if(gsr.smoothed > LED_SIG_THRESH) {
    digitalWrite(LEDPIN, HIGH);
  } else {
    digitalWrite(LEDPIN, LOW);
  };
}

void loop(){
  audioHook(); // MOZZI: wraps audio-rate callback
}





