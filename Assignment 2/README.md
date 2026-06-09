# Assignment 2: IoT — ESP32 LED Control via MQTT


Simulates an ESP32 microcontroller on [Wokwi](https://wokwi.com) and controls an LED remotely using the MQTT publish-subscribe protocol over a public broker. A Python script acts as the controller, publishing `on`/`off` commands to the ESP32 over the network.

---

## How It Works

```
Python Script  ──publish──▶  HiveMQ Broker  ──subscribe──▶  ESP32 (Wokwi)
(controller)                (broker.hivemq.com)              (LED on PIN 2)

Topic: buet/cse/2105114/led
```

1. The ESP32 connects to Wi-Fi and subscribes to the MQTT topic `buet/cse/2105114/led`
2. The Python script connects to the same broker and publishes `"on"` or `"off"` to that topic
3. The ESP32 receives the message and toggles the LED accordingly

---

## File Structure

```
Assignment 2/
├── sketch.ino               # ESP32 Arduino sketch — Wi-Fi + MQTT + LED control
├── diagram.json             # Wokwi circuit diagram (ESP32 + LED + resistor on PIN 2)
├── libraries.txt            # Wokwi library list (PubSubClient)
├── wokwi-project.txt        # Wokwi project metadata / simulation link
└── sample_for_control.py    # Python MQTT publisher — keyboard-controlled LED toggle
```

---

## Running the Simulation (ESP32 side)

1. Go to [wokwi.com](https://wokwi.com) and log in
2. Create a new **ESP32 + Arduino** project
3. Paste `sketch.ino` into the editor
4. Add the **PubSubClient** library via Library Manager
5. Set up the circuit: LED connected to **PIN 2** and **GND** through a resistor (see `diagram.json`)
6. Click **Run** — wait for `Wi-Fi connected!` and `connected` in the serial monitor

---

## Running the Python Controller

**Install dependency:**
```bash
pip install paho-mqtt
```

**Run the controller:**
```bash
python sample_for_control.py
```

**Controls:**
| Key | Action |
|-----|--------|
| `y` | Turn LED **ON** (publishes `"on"`) |
| `n` | Turn LED **OFF** (publishes `"off"`) |
| `q` | Quit |

> Note: Uses a public MQTT broker (`broker.hivemq.com`) — an occasional message drop is normal.

---

## Configuration

| Parameter | Value |
|-----------|-------|
| MQTT Broker | `broker.hivemq.com` |
| Port | `1883` |
| Topic | `buet/cse/2105114/led` |
| Wi-Fi (Wokwi) | `Wokwi-GUEST` (no password) |
| LED Pin | GPIO 2 |

