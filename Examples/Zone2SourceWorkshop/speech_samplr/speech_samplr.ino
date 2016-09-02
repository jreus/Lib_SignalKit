
/*  Example of playing a sampled sound,
    using Mozzi sonification library.
  
    Demonstrates one-shot samples scheduled
    with EventDelay.
  
    Circuit: Audio output on digital pin 9 on a Uno or similar, or
  
    Mozzi help/discussion/announcements:
    https://groups.google.com/forum/#!forum/mozzi-users
  
    Tim Barrass 2012, CC by-nc-sa.
*/

#include <MozziGuts.h>
#include <Sample.h> // Sample template
#include <g_R.h> // this is your sample file
#include <SKgsr.h>
#include <QuickSerial.h>


// Mozzi Config
#define AUDIO_MODE STANDARD
#define SEND_SERIAL 25 // comment this line to disable serial feedback, number is the serial tick rate in ms
#define SERIAL_BAUD 115200 // standard for Mozzi
#define AUDIO_RATE 16384
#define USE_AREF_EXTERNAL false

// Mozzi extras
#include <IntMap.h> // a faster version of Arduino's map
#include <Smooth.h> // smooth out AC interference
#include <Metronome.h> // used for sending serial data @ 20fps rather than send a data overload
#include <DCfilter.h> // a DC filter


#define CONTROL_RATE 64
#define GSRPIN A0
#define LEDPIN 8

// thresholding
#define SIG_THRESH 200
#define DC_SIG_CENTER 2048

#define SMOOTHNESS 0.70 // float between 0.0-1.0 - where 1.0 is very smooth
SKgsr gsr(GSRPIN, SMOOTHNESS);


// DC filter for gsr
DCfilter dcGSR(0.95);

Metronome metro;
byte tickCounter = 0;

QuickSerial serial;
Metronome serialTick;
byte serialCounter = 0;

// use: Sample <table_size, update_rate> SampleName (wavetable)
Sample <g_R_NUM_CELLS, AUDIO_RATE> aSample(g_R_DATA);

// Data cooking
IntMap map_gsr_metro(0, 4095, 2000, 300); 
IntMap map_gsr_gain(0, 4095, 4, 100); 
IntMap map_gsr_pitch(0, 4095, 20, 80); 


byte gain = 128;
uint16_t metro_rate_ms = 250;
float playspeed = 1.0;

void setup(){
#ifdef SEND_SERIAL
  serial.init(SERIAL_BAUD);
  delay(100); // don't use delay after starting Mozzi
  serial.println("Hello");
  serialTick.start(SEND_SERIAL);
#endif
  pinMode(LEDPIN, OUTPUT);
  delay(100);
  // Startup Signal
  for(int i = 0; i < 10; i++) {
    digitalWrite(LEDPIN, HIGH);
    delay(100);
    digitalWrite(LEDPIN, LOW);
    delay(50);
  }

  startMozzi(CONTROL_RATE);
  // play at the speed it was recorded
  aSample.setFreq((float) g_R_SAMPLERATE / (float) g_R_NUM_CELLS); 
  //aSample.setLoopingOn();
  metro.start(metro_rate_ms);
}


void updateControl(){
  uint16_t gsrval;
  // read the ADC input for the biosensors & do any signal processing here
  gsr.readSample();
  // remove DC offset
  gsrval = dcGSR.next(gsr.oversampled) + DC_SIG_CENTER; 


#ifdef SEND_SERIAL
  if(serialTick.ready()) {
    switch(serialCounter % 3) 
    {
      case 0: serial.write('G', gsr.oversampled); // raw gsr
      break;
      case 1: serial.write('S', gsrval); // DC removed
      break;
      case 2: serial.write('R', gsr.smoothed); // smoothed
      break;
    };
    serialCounter++;
  };
#endif  


  if(metro.ready()){
    aSample.setFreq(playspeed*((float) g_R_SAMPLERATE / (float) g_R_NUM_CELLS));
    //metro.set(metro_rate_ms);
    if(gsrval > (DC_SIG_CENTER + SIG_THRESH) && (!aSample.isPlaying())) {
      aSample.start();
    }
    tickCounter++;
  }

  gain = map_gsr_gain(gsrval);
  playspeed = map_gsr_pitch(gsrval) / 64.0F;
  //metro_rate_ms = map_gsr_metro(gsrval);
  
  if(gsrval > (DC_SIG_CENTER + SIG_THRESH)) {
    digitalWrite(LEDPIN, HIGH);
  } else {
    digitalWrite(LEDPIN, LOW);
  };
  
}

int updateAudio(){
  int sig1=0;
  sig1 = (int)aSample.next();
  return (sig1 * gain) >> 8;
}

void loop(){
  audioHook();
}



