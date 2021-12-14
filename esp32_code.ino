/*
   Create a BLE server that, once we receive a connection, will send periodic notifications.
   The service advertises itself as: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
   Has a characteristic of: 6E400002-B5A3-F393-E0A9-E50E24DCCA9E - used for receiving data with "WRITE" 
   Has a characteristic of: 6E400003-B5A3-F393-E0A9-E50E24DCCA9E - used to send data with  "NOTIFY"

   The design of creating the BLE server is:
   1. Create a BLE Server
   2. Create a BLE Service
   3. Create a BLE Characteristic on the Service
   4. Create a BLE Descriptor on the characteristic
   5. Start the service.
   6. Start advertising.
*/

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#include <time.h>
#include <sys/time.h>

#include <TinyGPS++.h>
#include <SoftwareSerial.h>

char * codage_xor(char * texte, int taille, char * cle) {
  int c_cle = 0;
  for (int c_txt = 0; c_txt < taille; c_txt++) { // pour toute la chaine
    texte[c_txt] ^= cle[c_cle++];        // XOR du car. avec un car. de la clé
    if (!cle[c_cle]) {                   // si on est au bout de la clé
      c_cle = 0;                       // on boucle
    }
  }
  return texte;
} // FIN de fonction

char *key = "1234567812345678123456781234567";

BLECharacteristic *pCharacteristic;
bool deviceConnected = false;
float txValue = 0;
const int readPin = 32; // Use GPIO number. See ESP32 board pinouts
const int LED = 2; // Could be different depending on the dev board. I used the DOIT ESP32 dev board.

//std::string rxValue; // Could also make this a global var to access it in loop()

// See the following for generating UUIDs:
// https://www.uuidgenerator.net/

#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E" // UART service UUID
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

// Time
#define time_offset   19800  // define a clock offset. ex: 3600 seconds (1 hour) ==> UTC + 1
bool is_time_set = false;

// GPS constants
static const int RXPin = 16, TXPin = 17;// Here we make pin 4 as RX of arduino & pin 3 as TX of arduino 
static const uint32_t GPSBaud = 9600;
TinyGPSPlus gps;
SoftwareSerial ss(RXPin, TXPin);

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string rxValue = pCharacteristic->getValue();

      if (rxValue.length() > 0) {
        Serial.println("*********");
        Serial.print("Received Value: ");

        for (int i = 0; i < rxValue.length(); i++) {
          Serial.print(rxValue[i]);
        }

        Serial.println();

        // Check timestamp
        long int timestamp;
        int tsPos = 3; // 1-indexed
        char* payload = strdup(rxValue.c_str());
        char* item = strtok(payload, "|");
        while (item != 0)
        {
          if (!--tsPos) {
            timestamp = atol(item);
          }
          
          // Find the next command in input string
          item = strtok(0, "&");
        }
        Serial.println("Timestamp in response: " + timestamp);
        free(payload);

        // Actions
        if (rxValue.find("A|2|") != -1) {
          Serial.print("Unlocking!");
          digitalWrite(LED, HIGH);
        }
        else if (rxValue.find("A|3") != -1) {
          Serial.print("Locking!");
          digitalWrite(LED, LOW);
        }

        Serial.println();
        Serial.println("*********");
      }
    }
};

void setup() {
  Serial.begin(115200);
  ss.begin(GPSBaud);

  pinMode(LED, OUTPUT);

  // Create the BLE Device
  BLEDevice::init("ESP32 UART Test"); // Give it a name

  // Create the BLE Server
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create a BLE Characteristic
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID_TX,
                      BLECharacteristic::PROPERTY_NOTIFY
                    );
                      
  pCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic *pCharacteristic = pService->createCharacteristic(
                                         CHARACTERISTIC_UUID_RX,
                                         BLECharacteristic::PROPERTY_WRITE
                                       );

  pCharacteristic->setCallbacks(new MyCallbacks());

  // Start the service
  pService->start();

  // Start advertising
  pServer->getAdvertising()->start();
  Serial.println("Waiting a client connection to notify...");
}

String locationValue = F("");

void loop() {
  while (ss.available() > 0) {
    char gpsData = ss.read();
//    Serial.print(gpsData);
    if (gps.encode(gpsData)) {
      if (gps.location.isValid()) {
        locationValue = String(gps.location.lat(), 6) + F("|") + String(gps.location.lng(), 6);
//        Serial.println("New loc value: " + locationValue);
      } else {
        locationValue = F("");
        Serial.println("Invalid GPS coords.");
      }
    }
  }
  if (!is_time_set && gps.date.value()) {
    setTime(
      gps.date.year(),
      gps.date.month(),
      gps.date.day(),
      gps.time.hour(),
      gps.time.minute(),
      gps.time.second()
    );
  }
//  Serial.println();
  Serial.print("LAT=");  Serial.print(gps.location.lat(), 6);
  Serial.print("\tLONG="); Serial.print(gps.location.lng(), 6);
  Serial.print("\tALT=");  Serial.print(gps.altitude.meters());
  Serial.print("\tDT=");  Serial.println(String(gps.date.value())+ F(" ") + String(gps.time.value()));
//  Serial.println();

  struct timeval tv_now;
  gettimeofday(&tv_now, NULL);
  Serial.print("Sys time: ");  Serial.println(ctime((const time_t *) &tv_now.tv_sec));

//  Serial.print("Sentences that failed checksum=");
//  Serial.println(gps.failedChecksum());

  if (millis() > 10000 && gps.charsProcessed() < 10)
  {
    Serial.println(F("No GPS detected: check wiring."));
    while(true);
  }
  
  if (deviceConnected) {
//    pCharacteristic->setValue(&txValue, 1); // To send the integer value
//    pCharacteristic->setValue("Hello!"); // Sending a test message
//    pCharacteristic->setValue(txString);

    pCharacteristic->setValue(locationValue.c_str());
    
    pCharacteristic->notify(); // Send the value to the app!
    Serial.print("*** Sent Value: ");
//    Serial.print(txString);
    Serial.print(locationValue);
    Serial.println(" ***");
  }
  delay(200);
}


void setTime(int y, int mo, int d, int h, int mi, int s) {
  struct tm tm;
  tm.tm_year = y - 1900;
  tm.tm_mon = mo;
  tm.tm_mday = d;
  tm.tm_hour = h;
  tm.tm_min = mi;
  tm.tm_sec = s;
  time_t t = mktime(&tm);
  printf("Setting time: %s", asctime(&tm));
  struct timeval now = { .tv_sec = t };
  settimeofday(&now, NULL);
  is_time_set = true;
}
