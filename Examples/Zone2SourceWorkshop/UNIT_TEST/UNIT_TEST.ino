/*  
 *  Testing Firmware
  
    Smooths & Removes DC-offset from GSR signal, then applies a constraint
    and sensitivity range. 

    GSR signal is sonified directly:
    - White Noise gain follows signal value
    - Filter frequency follows signal value
    - Sample trigger at a specific threshhold
  
    Circuit: Audio output on digital pin 9 on a Uno or similar, or
  
    Mozzi help/discussion/announcements:
    https://groups.google.com/forum/#!forum/mozzi-users
  
    Tim Barrass 2012, CC by-nc-sa.
*/

#include <MozziGuts.h>
#include <Line.h>
#include <mozzi_rand.h>
#include <StateVariable.h>
#include <SKgsr.h>
#include <QuickSerial.h>

// Mozzi extras
#include <IntMap.h> // a faster version of Arduino's map
#include <Smooth.h> // smooth out AC interference
#include <DCfilter.h> // a DC filter
#include <Metronome.h> // used for sending serial data @ 20fps rather than send a data overload


// Mozzi Config
#define AUDIO_MODE STANDARD
#define SEND_SERIAL 25 // comment this line to disable serial feedback, number is the serial tick rate in ms
#define SERIAL_BAUD 115200 // standard for Mozzi
#define AUDIO_RATE 16384
#define USE_AREF_EXTERNAL false

#define CONTROL_RATE 64
#define GSRPIN A0
#define LEDPIN 8

// signal constraints
#define DC_SIG_CENTER 2048
#define SIG_THRESH_TRIG DC_SIG_CENTER+200
#define SIG_THRESH_HIGH DC_SIG_CENTER+300
#define SIG_THRESH_LOW DC_SIG_CENTER-50

// Data cooking
IntMap map_gsr_gain(SIG_THRESH_LOW, SIG_THRESH_HIGH, 1, 130); 
IntMap map_gsr_pitch(SIG_THRESH_LOW, SIG_THRESH_HIGH, 2000, 3000); 

#define SMOOTHNESS 0.70f // float between 0.0-1.0 - where 1.0 is very smooth
#define DC_COEFF 0.95f // we want this to follow the natural DC filter response of the sensor, it should let through a lot of low frequency changes
SKgsr gsr(GSRPIN, SMOOTHNESS);


// Control data filtering for gsr
Smooth<uint16_t> smoothGSR(SMOOTHNESS);
DCfilter dcGSR(DC_COEFF); // remove DC offset

Metronome metro;
byte tickCounter = 0;

QuickSerial serial;
Metronome serialTick;
byte serialCounter = 0;

// Audio Synthesis
StateVariable <BANDPASS> filter; // can be LOWPASS, BANDPASS, HIGHPASS or NOTCH

// use: Sample <table_size, update_rate> SampleName (wavetable)
//Sample <g_R_NUM_CELLS, AUDIO_RATE> aSample(g_R_DATA);
// Oscil <WHITENOISE8192_NUM_CELLS, AUDIO_RATE> aNoise(WHITENOISE8192_DATA); // audio noise
// Oscil<SIN2048_NUM_CELLS, CONTROL_RATE> kTremelo(SIN2048_DATA);
// a line to interpolate control tremolo at audio rate
//Line <unsigned int> aGain;


byte gain;
uint16_t center_freq = 2000;
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
  //aSample.setFreq((float) g_R_SAMPLERATE / (float) g_R_NUM_CELLS); 
  //aSample.setLoopingOn();
  //kTremelo.setFreq(10.5f);
  // cast to float because the resulting freq will be small and fractional
  //aNoise.setFreq((float)AUDIO_RATE/WHITENOISE8192_SAMPLERATE);
  metro.start(metro_rate_ms);
  filter.setResonance(120); // a byte, the lower the value the higher the resonance
  filter.setCentreFreq(center_freq);
  
}


void updateControl(){
  uint16_t gsrsmooth, gsrdc, gsrval;
  // read the ADC input for the biosensors & do any signal processing here
  gsr.readSample();
  // remove DC offset
  gsrsmooth = smoothGSR.next(gsr.oversampled); 
  gsrdc = dcGSR.next(gsrsmooth) + DC_SIG_CENTER;
  gsrval = constrain(gsrdc, SIG_THRESH_LOW, SIG_THRESH_HIGH); 


#ifdef SEND_SERIAL
  if(serialTick.ready()) {
    switch(serialCounter % 4) 
    {
      case 0: serial.write('G', gsr.oversampled); // raw gsr
      break;
      case 1: serial.write('S', gsrsmooth); // smoothed gsr
      break;
      case 2: serial.write('R', gsrdc); // DC removed
      break;
      case 3: serial.write('V', gsrval); // computed GSR
      break;
    };
    serialCounter++;
  };
#endif  

  if(metro.ready()){
    //aSample.setFreq(playspeed*((float) g_R_SAMPLERATE / (float) g_R_NUM_CELLS));
    //metro.set(metro_rate_ms);
    //if(gsrval > (DC_SIG_CENTER + SIG_THRESH_TRIG) && (!aSample.isPlaying())) {
      //aSample.start();
    //}
    tickCounter++;
  }

  gain = (byte)map_gsr_gain(gsrval);
  //aGain.set(gain, AUDIO_RATE / CONTROL_RATE); // divide of literals should get optimised away
  center_freq = map_gsr_pitch(gsrval);
  filter.setCentreFreq(center_freq);
  playspeed = map_gsr_pitch(gsrval) / 64.0F;
  //metro_rate_ms = map_gsr_metro(gsrval);
  
  if(gsrval > SIG_THRESH_TRIG) {
    digitalWrite(LEDPIN, HIGH);
  } else {
    digitalWrite(LEDPIN, LOW);
  };
  
}

int updateAudio(){
  int sig, sig1=0, sig2=0;
  //sig1 = (int)aSample.next();
  //sig2 = (int)aNoise.next();
  sig2 = rand();
  //return (int)(((long)sig1 + (long)sig2) * aGain.next()) >> 8;
  sig = (filter.next(sig2 >> 8) * gain) >> 8;
  return sig;
}

void loop(){
  audioHook();
}



