# Valid IL Instructions with Examples

IEC 61131-3 Instruction List (IL) is a text-based programming language that resembles assembly language. Here's a comprehensive list of valid IL instructions with multiple examples of how they're used in different scenarios.

## Basic Logic Operations

### LD (Load)
Loads a boolean value into the accumulator.
```
LD X1       ; Load value of input X1
LD TRUE     ; Load constant TRUE
LD #2       ; Load constant value 2
```

### LDN (Load Not)
Loads the inverted boolean value into the accumulator.
```
LDN X1      ; Load inverted value of input X1
LDN Switch1 ; Load inverted value of Switch1
```

### ST (Store)
Stores the accumulator value to a variable.
```
LD X1
ST Y1       ; Store result to output Y1
LD Temp
ST Result   ; Store Temp value to Result
```

### STN (Store Not)
Stores the inverted accumulator value to a variable.
```
LD X1
STN Y1      ; Store inverted result to output Y1
```

### AND
Performs logical AND with the accumulator.
```
LD X1
AND X2      ; AND X1 with X2
LD Switch1
AND Timer.Q ; AND Switch1 with Timer.Q output
```

### ANDN (AND Not)
Performs logical AND with the inverted operand.
```
LD X1
ANDN X2     ; AND X1 with NOT X2
LD Running
ANDN Error  ; AND Running with NOT Error
```

### OR
Performs logical OR with the accumulator.
```
LD X1
OR X2       ; OR X1 with X2
LD Button1
OR Button2  ; OR Button1 with Button2
```

### ORN (OR Not)
Performs logical OR with the inverted operand.
```
LD X1
ORN X2      ; OR X1 with NOT X2
LD Manual
ORN Auto    ; OR Manual with NOT Auto
```

### XOR (Exclusive OR)
Performs exclusive OR with the accumulator.
```
LD X1
XOR X2      ; XOR X1 with X2
LD Switch1
XOR Switch2 ; Lights if only one switch is on
```

### NOT
Inverts the accumulator value.
```
LD X1
NOT         ; Invert X1
LD Running
NOT         ; Invert Running state
```

## Modifiers

IL instructions can use modifiers to change their behavior:

### Parenthesized Execution
```
LD X1
AND(         ; Start a block
  LD X2
  OR X3
)            ; End the block
ST Y1
```

### Conditional Execution
```
LD X1
JMPC Label   ; Jump to Label if X1 is TRUE
LD X2
ST Y1
Label: LD X3 ; Label for jump target
```

## Timer Operations

### TON (Timer On Delay)
```
LD X1
TON T1, T#5s ; Start timer T1 with 5s preset when X1 is TRUE
LD T1.Q
ST Y1        ; Y1 is TRUE after 5s of X1 being TRUE
```

### TOF (Timer Off Delay)
```
LD X1
TOF T2, T#2s ; Start timer T2 with 2s preset when X1 becomes FALSE
LD T2.Q
ST Y1        ; Y1 stays TRUE for 2s after X1 becomes FALSE
```

### TP (Pulse Timer)
```
LD X1
TP T3, T#1s  ; Generate a 1s pulse when X1 becomes TRUE
LD T3.Q
ST Y1        ; Y1 is TRUE for 1s after X1 becomes TRUE
```

## Counter Operations

### CTU (Count Up)
```
LD X1
CTU C1, 10   ; Count up to 10 on rising edges of X1
LD X2
R C1         ; Reset counter when X2 is TRUE
LD C1.Q
ST Y1        ; Y1 is TRUE when counter reaches preset
```

### CTD (Count Down)
```
LD X1
CTD C2, 5    ; Count down from 5 on rising edges of X1
LD X2
LD C2        ; Load counter with preset value when X2 is TRUE
LD C2.Q
ST Y1        ; Y1 is TRUE when counter reaches zero
```

### CTUD (Count Up/Down)
```
LD X1
CTUD C3, X2, 15  ; Count up on X1 rising edge, down on X2
LD X3
R C3             ; Reset counter when X3 is TRUE
LD C3.Q
ST Y1            ; Y1 is TRUE when counter reaches preset
```

## Math Operations

### ADD (Addition)
```
LD Value1
ADD 10       ; Add 10 to Value1
ADD Value2   ; Add Value2 to the result
ST Result    ; Store the sum
```

### SUB (Subtraction)
```
LD Value1
SUB 5        ; Subtract 5 from Value1
SUB Value2   ; Subtract Value2 from the result
ST Difference ; Store the difference
```

### MUL (Multiplication)
```
LD Value1
MUL 2        ; Multiply Value1 by 2
MUL Value2   ; Multiply the result by Value2
ST Product   ; Store the product
```

### DIV (Division)
```
LD Value1
DIV 4        ; Divide Value1 by 4
DIV Value2   ; Divide the result by Value2
ST Quotient  ; Store the quotient
```

## Comparison Operations

### GT (Greater Than)
```
LD Temp
GT 25        ; TRUE if Temp > 25
ST Overheat  ; Set Overheat if temperature is too high
```

### GE (Greater Than or Equal)
```
LD Level
GE MinLevel  ; TRUE if Level >= MinLevel
ST OK        ; Set OK if level is acceptable
```

### EQ (Equal)
```
LD Position
EQ TargetPos ; TRUE if Position = TargetPos
ST InPosition ; Set InPosition when target is reached
```

### NE (Not Equal)
```
LD State
NE 0         ; TRUE if State ≠ 0
ST Active    ; Set Active if state is not zero
```

### LE (Less Than or Equal)
```
LD Pressure
LE MaxPressure ; TRUE if Pressure <= MaxPressure
ST Safe      ; Set Safe if pressure is within limits
```

### LT (Less Than)
```
LD Battery
LT 20        ; TRUE if Battery < 20%
ST LowBattery ; Set LowBattery if battery level is low
```

## Complex Example Programs

### Motor Control with Interlocks
```
; Start motor if Start button pressed AND no faults
LD StartButton
ANDN StopButton
ANDN Fault
ST MotorRun

; Latch motor running state
LD MotorRun
OR MotorRunning
ANDN StopButton
ANDN Fault
ST MotorRunning

; Motor output with delay
LD MotorRunning
TON StartDelay, T#2s
ST Motor
```

### Temperature Control with Hysteresis
```
; Heating control with hysteresis
LD CurrentTemp
LT SetPoint
ST HeatingNeeded

LD CurrentTemp
GT SetPoint
ADD 2        ; 2 degrees hysteresis
ST TooHot

LD HeatingNeeded
ANDN TooHot
TON HeaterDelay, T#30s  ; Delay before activating heater
ST Heater
```

### Conveyor Belt with Counter
```
; Count items on conveyor
LD ItemSensor
CTU ItemCounter, 100
ST CounterValue

; Reset counter when reset button pressed
LD ResetButton
R ItemCounter

; Full batch detection
LD ItemCounter.Q
ST BatchComplete

; Conveyor control
LD RunButton
ANDN BatchComplete
ANDN EmergencyStop
ST ConveyorMotor
```

### Sequential Process Control
```
; Step sequencer for a process
LD Start
ANDN Running
S Step1  ; Set Step1 when Start pressed and not running

LD Step1
TON Step1Timer, T#10s
R Step1  ; Reset Step1 after timer expires
S Step2  ; Set Step2

LD Step2
AND Sensor1
TON Step2Timer, T#5s
R Step2  ; Reset Step2 after timer expires and sensor active
S Step3  ; Set Step3

LD Step3
TON Step3Timer, T#15s
R Step3  ; Reset Step3 after timer expires
R Running  ; Reset Running state at end of sequence

; Set Running whenever in any step
LD Step1
OR Step2
OR Step3
ST Running

; Outputs for each step
LD Step1
ST Valve1

LD Step2
ST Mixer

LD Step3
ST Pump
```

### Alarm System with Latching
```
; Alarm triggers
LD TempSensor
GT 80
ST HighTempAlarm

LD PressureSensor
GT 500
ST HighPressureAlarm

; Latch any alarm condition
LD HighTempAlarm
OR HighPressureAlarm
OR AlarmActive
ANDN ResetButton
ST AlarmActive

; Alarm outputs
LD AlarmActive
ST AlarmLight

LD AlarmActive
AND ClockPulse  ; 1Hz pulse for flashing
ST AlarmSiren
```

These examples cover a wide range of IL programming patterns and techniques used in industrial control applications. Each example demonstrates how different IL instructions work together to create functional logic for automation systems.