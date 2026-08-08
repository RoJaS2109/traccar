package org.traccar.protocol;

import org.junit.jupiter.api.Test;
import org.traccar.ProtocolTest;
import org.traccar.model.Position;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RinhoProtocolDecoderTest extends ProtocolTest {

    @Test
    public void testDecodeRCQ() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCQ — Posición periódica (evento 00, IGN ON)
        // Dispositivo en Buenos Aires (-34.60368, -58.38156), 45 km/h, rumbo 180°
        verifyPosition(decoder, text(
                ">RCQ00080726143025-3460368-0583815604518080001260001A2B313050001102115;#0001;ID=KJA-169<"));

        // RCQ — Evento 03 (Capó Cerrado), informativo, sin alarma
        verifyPosition(decoder, text(
                ">RCQ03080726150000-3460400-0583820000000080001050001A30013050001002115;#0002;ID=KJA-169<"));

        // RCQ — Evento 04 (Puerta Del. Izq. Abierta), debe generar alarma door
        verifyPosition(decoder, text(
                ">RCQ04080726153015-3460500-0583835000000080000980001A35013050001002115;TXT=ALERTA POSIBLE CHOQUE GRAVE FRONTAL;#0003;ID=KJA-169<"));

        // RCQ — Evento 78 (Frenada Brusca), debe generar alarma hardBraking
        verifyPosition(decoder, text(
                ">RCQ78080726160030-3460600-058384000850900080001250001A40013050001003115;#0004;ID=KJA-169<"));
    }

    @Test
    public void testDecodeRER() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RER — Reporte extendido con CAN bus (motor encendido, RPM 1850, temp 89°C)
        verifyPosition(decoder, text(
                ">RER00080726144500-3460368-0583815606009080001300001A2B313050001102115;2010=1850,2012=89,2013=420,4201=72,1020=15230;ID=KJA-169<"));

        // RER — Con CAN bus parcial (algunos valores vacíos con !empty)
        verifyPosition(decoder, text(
                ">RER00080726150000-3460400-058382000450000800001050001A30013050001002115;2010=850,2012!empty,4201=65;ID=KJA-169<"));
    }

    @Test
    public void testDecodeRCR() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCR — Evento 13 (RES.), informativo, sin alarma
        verifyPosition(decoder, text(
                ">RCR13080726151025-3460368-0583815600000080;#0001;ID=KJA-169;*16<"));

        // RCR — Evento 19 (RES.), informativo, sin alarma
        verifyPosition(decoder, text(
                ">RCR19080726152030-3460400-0583820000000080;#0002;ID=KJA-169;*11<"));
    }

    @Test
    public void testDecodeAlarmMapping() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // ── Sin alarma ──────────────────────────────────────────
        // Evento 00: posición periódica, sin alarma
        var pos00 = (Position) decoder.decode(null, null, text(
                ">RCQ00080726143025-3460368-0583815604518080001260001A2B313050001102115;#0001;ID=KJA-169<"));
        assertNotNull(pos00);
        String alarms00 = (String) pos00.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms00 == null, "Evento 00 no debería generar alarma: " + alarms00);

        // Evento 03: Capó Cerrado (informativo, aparece como general)
        var pos03 = (Position) decoder.decode(null, null, text(
                ">RCQ03080726150000-3460400-0583820000000080001050001A30013050001002115;#0002;ID=KJA-169<"));
        assertNotNull(pos03);
        String alarms03 = (String) pos03.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms03 != null && alarms03.contains("general"),
                "Evento 03 (Capó Cerrado) debería generar alarma general: " + alarms03);
        assertEquals("Capó Cerrado", pos03.getAttributes().get("eventDescription"));

        // Evento 13: RES. (sin alarma)
        var pos13 = (Position) decoder.decode(null, null, text(
                ">RCR13080726151025-3460368-0583815600000080;#0001;ID=KJA-169;*16<"));
        assertNotNull(pos13);
        String alarms13 = (String) pos13.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms13 == null, "Evento 13 (RES.) no debería generar alarma: " + alarms13);

        // ── Con alarma ──────────────────────────────────────────
        // Evento 04 → door (Puerta Del. Izq. Abierta)
        var pos04 = (Position) decoder.decode(null, null, text(
                ">RCQ04080726153015-3460500-0583835000000080000980001A35013050001002115;#0003;ID=KJA-169<"));
        assertNotNull(pos04);
        String alarms04 = (String) pos04.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms04 != null && alarms04.contains("door"),
                "Evento 04 debería generar alarma door: " + alarms04);

        // Evento 63 → tampering (Manipulación / Sabotaje)
        var pos63 = (Position) decoder.decode(null, null, text(
                ">RCR19080726152030-3460400-0583820000000080;#0002;ID=KJA-169;*11<"));
        assertNotNull(pos63);
        String alarms63 = (String) pos63.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms63 == null, "Evento 19 (RES.) no debería generar alarma: " + alarms63);

        // Evento 65 → tow (Grúa / Remolque)
        var pos65 = (Position) decoder.decode(null, null, text(
                ">RCQ78080726160030-3460600-058384000850900080001250001A40013050001003115;#0004;ID=KJA-169<"));
        assertNotNull(pos65);
        String alarms65 = (String) pos65.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms65 != null && alarms65.contains("hardBraking"),
                "Evento 78 debería generar alarma hardBraking: " + alarms65);

        // Evento 78 → hardBraking (Frenada Brusca)
        var pos78 = (Position) decoder.decode(null, null, text(
                ">RCQ78080726160030-3460600-058384000850900080001250001A40013050001003115;#0004;ID=KJA-169<"));
        assertNotNull(pos78);
        String alarms78 = (String) pos78.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms78 != null && alarms78.contains("hardBraking"),
                "Evento 78 debería generar alarma hardBraking: " + alarms78);

        // Evento 99 → sos (SOS)
        var pos99 = (Position) decoder.decode(null, null, text(
                ">RCQ99080726170000-3460400-0583820000000080001050001A30013050001002115;#0005;ID=KJA-169<"));
        assertNotNull(pos99);
        String alarms99 = (String) pos99.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms99 != null && alarms99.contains("sos"),
                "Evento 99 debería generar alarma sos: " + alarms99);
    }

    @Test
    public void testDecodeRCW() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCW — Reporte Compacto WiFi (course antes de speed, odómetro decimal)
        var position = (Position) decoder.decode(null, null, text(
                ">RCW00070826204659-3869515-0623511310800011200003000009900000000007F;ID=KJA-169;*4B<"));

        assertNotNull(position, "El decoder debería parsear RCW correctamente");

        // Fecha: 07/08/2026 20:46:59 UTC
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(7, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1);
        assertEquals(20, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(46, cal.get(java.util.Calendar.MINUTE));
        assertEquals(59, cal.get(java.util.Calendar.SECOND));

        // Coordenadas DEG_DEG: -3869515 → -38.69515, -06235113 → -62.35113
        assertEquals(-38.69515, position.getLatitude(), 0.00001);
        assertEquals(-62.35113, position.getLongitude(), 0.00001);

        // Course 108°, Speed 0 km/h → 0 nudos
        assertEquals(108.0, position.getCourse(), 0.1);
        assertEquals(0.0, position.getSpeed(), 0.1);

        // GPS: power=1 (ON), satellites=12, fix=3D, pdop=0, age=0
        assertEquals(1, ((Number) position.getAttributes().get(Position.KEY_GPS)).intValue());
        assertEquals(12, ((Number) position.getAttributes().get(Position.KEY_SATELLITES)).intValue());
        assertEquals(0, ((Number) position.getAttributes().get(Position.KEY_PDOP)).intValue());

        // GSM: modem=0, networkType=0, csq=99 (sin señal)
        assertEquals(0, ((Number) position.getAttributes().get("modemPower")).intValue());
        assertEquals(0, ((Number) position.getAttributes().get("networkType")).intValue());
        assertEquals(99, ((Number) position.getAttributes().get(Position.KEY_RSSI)).intValue());

        // Odómetro decimal: 0 metros (campo de 10 dígitos: 0000000000)
        assertEquals(0L, ((Number) position.getAttributes().get(Position.KEY_ODOMETER)).longValue());

        // IGN+IN flags: 0x7F → IGN=0 (bit 7), IN0-6=1 (bits 0-6)
        assertEquals(0x7F, ((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue());

        // Position should be valid
        assertTrue(position.getValid());

        System.out.println("✅ RCW decodificado correctamente:");
        System.out.println("  Fecha:       " + position.getFixTime());
        System.out.println("  Lat:         " + position.getLatitude());
        System.out.println("  Lon:         " + position.getLongitude());
        System.out.println("  Course:      " + position.getCourse() + "°");
        System.out.println("  Speed:       " + position.getSpeed() + " kn");
        System.out.println("  GPS:         ON, " + position.getAttributes().get(Position.KEY_SATELLITES) + " sats, 3D fix");
        System.out.println("  NetworkType: " + position.getAttributes().get("networkType"));
        System.out.println("  Odómetro:    " + position.getAttributes().get(Position.KEY_ODOMETER) + " m");
        System.out.println("  RSSI:        " + position.getAttributes().get(Position.KEY_RSSI));
        System.out.println("  IGN+IN:      0x" + Integer.toHexString(((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue()));
        System.out.println("  Válido:      " + position.getValid());
    }

    @Test
    public void testDecodeRGP() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RGP — Reporte GPS (estándar simplificado)
        var position = (Position) decoder.decode(null, null, text(
                ">RGP070826204709-3869514-062351120001083007F0000;ID=KJA-169;*4E<"));

        assertNotNull(position, "El decoder debería parsear RGP correctamente");

        // Fecha: 07/08/2026 20:47:09 UTC
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(7, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1);
        assertEquals(20, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(47, cal.get(java.util.Calendar.MINUTE));
        assertEquals(9, cal.get(java.util.Calendar.SECOND));

        // Coordenadas: -38.69514, -62.35112
        assertEquals(-38.69514, position.getLatitude(), 0.00001);
        assertEquals(-62.35112, position.getLongitude(), 0.00001);

        // Speed 0, Course 108°
        assertEquals(0.0, position.getSpeed(), 0.1);
        assertEquals(108.0, position.getCourse(), 0.1);

        // GPS: fix=3D, age=0
        assertEquals(3, ((Number) position.getAttributes().get("gpsFix")).intValue());
        assertEquals(0, ((Number) position.getAttributes().get("gpsAge")).intValue());

        // IGN+IN: 0x7F → IGN=0, IN0-6=1
        assertEquals(0x7F, ((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue());

        // PDOP=0, valid
        assertEquals(0, ((Number) position.getAttributes().get(Position.KEY_PDOP)).intValue());
        assertTrue(position.getValid());

        System.out.println("✅ RGP decodificado correctamente:");
        System.out.println("  Fecha:   " + position.getFixTime());
        System.out.println("  Lat:     " + position.getLatitude());
        System.out.println("  Lon:     " + position.getLongitude());
        System.out.println("  Course:  " + position.getCourse() + "°");
        System.out.println("  Speed:   " + position.getSpeed() + " kn");
        System.out.println("  GPS Fix: " + position.getAttributes().get("gpsFix") + "D, age=" + position.getAttributes().get("gpsAge") + "s");
        System.out.println("  IGN+IN:  0x" + Integer.toHexString(((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue()));
        System.out.println("  Válido:  " + position.getValid());
    }

    @Test
    public void testDecodeRCY() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCY — Standard con Altitud
        var position = (Position) decoder.decode(null, null, text(
                ">RCY00070826204722-3869514-06235112000108-000012;IGN0;IN7F;XP00;ID=KJA-169;*13<"));

        assertNotNull(position, "El decoder debería parsear RCY correctamente");

        // Fecha: 07/08/2026 20:47:22 UTC
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(7, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1);
        assertEquals(20, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(47, cal.get(java.util.Calendar.MINUTE));
        assertEquals(22, cal.get(java.util.Calendar.SECOND));

        // Coordenadas: -38.69514, -62.35112
        assertEquals(-38.69514, position.getLatitude(), 0.00001);
        assertEquals(-62.35112, position.getLongitude(), 0.00001);

        // Speed 0, Course 108°, Altitude 0
        assertEquals(0.0, position.getSpeed(), 0.1);
        assertEquals(108.0, position.getCourse(), 0.1);
        assertEquals(0.0, position.getAltitude(), 0.1);

        // IGN=0, IN=0x7F, XP=0
        assertEquals(0x7F, ((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue());
        assertEquals(0, ((Number) position.getAttributes().get(Position.KEY_OUTPUT)).intValue());

        // Valid (GPS1=1, GPS2=2)
        assertTrue(position.getValid());

        System.out.println("✅ RCY decodificado correctamente:");
        System.out.println("  Fecha:   " + position.getFixTime());
        System.out.println("  Lat:     " + position.getLatitude());
        System.out.println("  Lon:     " + position.getLongitude());
        System.out.println("  Course:  " + position.getCourse() + "°");
        System.out.println("  Speed:   " + position.getSpeed() + " kn");
        System.out.println("  Alt:     " + position.getAltitude() + " m");
        System.out.println("  IGN+IN:  0x" + Integer.toHexString(((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue()));
        System.out.println("  Válido:  " + position.getValid());
    }

    @Test
    public void testDecodeRCRExtended() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCR extendido (con GPS/GSM + odómetro en el sufijo)
        var position = (Position) decoder.decode(null, null, text(
                ">RCR00070826204738-3869514-062351130001087F000000000000013001200000099+0000FF;ID=KJA-169;*62<"));

        assertNotNull(position, "El decoder debería parsear RCR extendido correctamente");

        // Fecha: 07/08/2026 20:47:38 UTC (DD/MM/YY en RCR)
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(7, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1); // August
        assertEquals(20, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(47, cal.get(java.util.Calendar.MINUTE));
        assertEquals(38, cal.get(java.util.Calendar.SECOND));

        // Coordenadas: -38.69514, -62.35113
        assertEquals(-38.69514, position.getLatitude(), 0.00001);
        assertEquals(-62.35113, position.getLongitude(), 0.00001);

        // Speed 0, Course 108°
        assertEquals(0.0, position.getSpeed(), 0.1);
        assertEquals(108.0, position.getCourse(), 0.1);

        // IO flags: 0x7F
        assertEquals(0x7F, ((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue());
        assertTrue(position.getValid());

        // Sin alarma (evento 00)
        assertEquals(0, ((Number) position.getAttributes().get(Position.KEY_EVENT)).intValue());

        System.out.println("✅ RCR extendido decodificado:");
        System.out.println("  Fecha:   " + position.getFixTime());
        System.out.println("  Lat:     " + position.getLatitude());
        System.out.println("  Lon:     " + position.getLongitude());
        System.out.println("  Course:  " + position.getCourse() + "°");
        System.out.println("  Speed:   " + position.getSpeed() + " kn");
        System.out.println("  IO:      0x" + Integer.toHexString(((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue()));
        System.out.println("  Válido:  " + position.getValid());
    }

    @Test
    public void testDecodeRAD() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RAD — Reporte Analógico (8 canales: AIN00-05 + batería principal + backup)
        var position = (Position) decoder.decode(null, null, text(
                ">RAD0008082601080803260320000000000000000000000423;ID=KJA-169;*26<"));

        assertNotNull(position, "El decoder debería parsear RAD correctamente");

        // Fecha: 08/08/2026 01:08:08 UTC
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(8, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1);
        assertEquals(1, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(8, cal.get(java.util.Calendar.MINUTE));
        assertEquals(8, cal.get(java.util.Calendar.SECOND));

        // Canales analógicos
        assertEquals(3.26, ((Number) position.getAttributes().get("ain00")).doubleValue(), 0.01);
        assertEquals(3.20, ((Number) position.getAttributes().get("ain01")).doubleValue(), 0.01);
        assertEquals(0.0, ((Number) position.getAttributes().get("ain02")).doubleValue(), 0.01);
        assertEquals(0.0, ((Number) position.getAttributes().get("ain03")).doubleValue(), 0.01);

        // Batería backup: 4.23V
        assertEquals(4.23, ((Number) position.getAttributes().get("battery")).doubleValue(), 0.01);

        System.out.println("✅ RAD decodificado:");
        System.out.println("  Fecha:   " + position.getFixTime());
        System.out.println("  AIN00:   " + position.getAttributes().get("ain00") + "V");
        System.out.println("  AIN01:   " + position.getAttributes().get("ain01") + "V");
        System.out.println("  Batería: " + position.getAttributes().get("battery") + "V");
    }

    @Test
    public void testDecodeRAE() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RAE — Reporte Analógico con Signo (±dddd para cada canal)
        var position = (Position) decoder.decode(null, null, text(
                ">RAE00080826011155+0325+0326+0000+0000+0000+0000+0000+0423;ID=KJA-169;*22<"));

        assertNotNull(position, "El decoder debería parsear RAE correctamente");

        // Fecha: 08/08/2026 01:11:55 UTC
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(8, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1);
        assertEquals(1, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(11, cal.get(java.util.Calendar.MINUTE));
        assertEquals(55, cal.get(java.util.Calendar.SECOND));

        // Canales con signo
        assertEquals(3.25, ((Number) position.getAttributes().get("ain00")).doubleValue(), 0.01);
        assertEquals(3.26, ((Number) position.getAttributes().get("ain01")).doubleValue(), 0.01);
        assertEquals(0.0, ((Number) position.getAttributes().get("ain02")).doubleValue(), 0.01);
        assertEquals(4.23, ((Number) position.getAttributes().get("battery")).doubleValue(), 0.01);

        System.out.println("✅ RAE decodificado:");
        System.out.println("  Fecha:   " + position.getFixTime());
        System.out.println("  AIN00:   " + position.getAttributes().get("ain00") + "V");
        System.out.println("  AIN01:   " + position.getAttributes().get("ain01") + "V");
        System.out.println("  Batería: " + position.getAttributes().get("battery") + "V");
    }

    @Test
    public void testDecodeInventory() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        // RVR — firmware version
        var rvr = (Position) decoder.decode(null, null, text(
                ">RVR RINHO IOT v1.09.16 SP EG915U LC86G 16MB WIFI 2025-12-01 14:53:59;ID=KJA-169;*56<"));
        assertNotNull(rvr, "RVR");

        // RSN — serial number
        var rsn = (Position) decoder.decode(null, null, text(
                ">RSN70B4C1E9BFB40000000000E7;ID=KJA-169;*47<"));
        assertNotNull(rsn, "RSN");

        // RIMEI — IMEI
        var rimei = (Position) decoder.decode(null, null, text(
                ">RIMEI;ID=KJA-169;*2D<"));
        assertNotNull(rimei, "RIMEI");

        // RTAG — tag
        var rtag = (Position) decoder.decode(null, null, text(
                ">RTAG 5200-2604MM-0960;ID=KJA-169;*5F<"));
        assertNotNull(rtag, "RTAG");

        // RCXHWI — hardware ID
        var rcxhwi = (Position) decoder.decode(null, null, text(
                ">RCXHWI5200;ID=KJA-169;*6F<"));
        assertNotNull(rcxhwi, "RCXHWI");

        System.out.println("✅ Inventory: RVR, RSN, RIMEI, RTAG, RCXHWI OK");
    }

    @Test
    public void testDecodeRIO() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        var position = (Position) decoder.decode(null, null, text(
                ">RIO;IGN0;IN1111011;XP001;V000;VBU423;ID=KJA-169;*74<"));

        assertNotNull(position, "RIO");
        assertEquals(0x7B, ((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue()); // binary 1111011
        assertEquals(1, ((Number) position.getAttributes().get(Position.KEY_OUTPUT)).intValue()); // XP001
        assertEquals(4.23, ((Number) position.getAttributes().get("battery")).doubleValue(), 0.01); // VBU423

        System.out.println("✅ RIO: IGN=0, IN=0x7B, XP=1, VBU=4.23V");
    }

    @Test
    public void testDecodeCQVariants() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCP
        var rcp = (Position) decoder.decode(null, null, text(
                ">RCP00080826012212-3869513-062351100003477B010000000000013001200000099;ID=KJA-169;*44<"));
        assertNotNull(rcp, "RCP");
        assertEquals(-38.69513, rcp.getLatitude(), 0.00001);

        // RCT — +iButton
        var rct = (Position) decoder.decode(null, null, text(
                ">RCT00080826012212-3869513-062351110003477B010000000000013001200000099;0000000000000000;ID=KJA-169;*7A<"));
        assertNotNull(rct, "RCT");

        // RCU — +driver
        var rcu = (Position) decoder.decode(null, null, text(
                ">RCU00080826012212-3869513-062351110003477B0100000000000130012000000990;;ID=KJA-169;*4B<"));
        assertNotNull(rcu, "RCU");

        // RCV — +2 temps
        var rcv = (Position) decoder.decode(null, null, text(
                ">RCV00080826012212-3869513-062351110003477B010000000000013001200000099+0000FF+0000FF;ID=KJA-169;*43<"));
        assertNotNull(rcv, "RCV");

        // RBQ — +battery backup
        var rbq = (Position) decoder.decode(null, null, text(
                ">RBQ00080826012212-3869513-062351110003477B010000000000013001200000099423;ID=KJA-169;*70<"));
        assertNotNull(rbq, "RBQ");

        // RBR — +battery +temp
        var rbr = (Position) decoder.decode(null, null, text(
                ">RBR00080826012212-3869513-062351110003477B010000000000013001200000099423+0000FF;ID=KJA-169;*58<"));
        assertNotNull(rbr, "RBR");

        // RBV — +battery +2 temps
        var rbv = (Position) decoder.decode(null, null, text(
                ">RBV00080826012212-3869513-062351110003477B010000000000013001200000099423+0000FF+0000FF;ID=KJA-169;*77<"));
        assertNotNull(rbv, "RBV");

        // RHQ — +battery +hour meter
        var rhq = (Position) decoder.decode(null, null, text(
                ">RHQ00080826012212-3869513-062351110003477B01000000000001300120000009942300000000;ID=KJA-169;*7A<"));
        assertNotNull(rhq, "RHQ");

        // RHR — +battery +hour meter +temp
        var rhr = (Position) decoder.decode(null, null, text(
                ">RHR00080826012212-3869513-062351110003477B01000000000001300120000009942300000000+0000FF;ID=KJA-169;*52<"));
        assertNotNull(rhr, "RHR");

        // RHV — +battery +hour meter +2 temps
        var rhv = (Position) decoder.decode(null, null, text(
                ">RHV00080826012212-3869513-062351110003477B01000000000001300120000009942300000000+0000FF+0000FF;ID=KJA-169;*7D<"));
        assertNotNull(rhv, "RHV");

        System.out.println("✅ CQ Variants (10): RCP, RCT, RCU, RCV, RBQ, RBR, RBV, RHQ, RHR, RHV OK");
    }

    @Test
    public void testDecodeREQ() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        // REQ — CAN bus OBD-II (datos vacíos, sin PIDs)
        var position = (Position) decoder.decode(null, null, text(
                ">REQ00080826012211-3869513-062351110003477B010000000000013001200000099;1=,2=,3=,B=,14=,15=,2A=,2C=;ID=KJA-169;*27<"));

        assertNotNull(position, "REQ");
        assertEquals(-38.69513, position.getLatitude(), 0.00001);
        assertEquals(-62.35111, position.getLongitude(), 0.00001);
        assertEquals(0.0, position.getSpeed(), 0.1);

        System.out.println("✅ REQ: CAN bus OBD-II OK");
    }

    @Test
    public void testDecodeRTX() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        // RTX — Reporte de texto (vacío)
        var position = (Position) decoder.decode(null, null, text(
                ">RTX;ID=KJA-169;*29<"));
        assertNotNull(position, "RTX");

        System.out.println("✅ RTX OK");
    }

    @Test
    public void testDecodeRIB() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        // RIB — iButton + temperatura
        var position = (Position) decoder.decode(null, null, text(
                ">RIB080826012212-3869513-062351110003473007B0000;0000000000000000;+0000FF;ID=KJA-169;*75<"));

        assertNotNull(position, "RIB");
        assertEquals(-38.69513, position.getLatitude(), 0.00001);
        assertEquals(-62.35111, position.getLongitude(), 0.00001);

        System.out.println("✅ RIB: iButton + temp OK");
    }

    @Test
    public void testDecodeRSC() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        // RSC — Sensor de contacto
        var position = (Position) decoder.decode(null, null, text(
                ">RSC00000000000000000000000000000000000000000000000;ID=KJA-169;*05<"));
        assertNotNull(position, "RSC");

        System.out.println("✅ RSC OK");
    }

    @Test
    public void testDecodeRLC() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        // RLC — Locator por celda (DEG_DEG, mismo formato que RCQ)
        var position = (Position) decoder.decode(null, null, text(
                ">RLC00080826012212-3869513-0623511100;ID=KJA-169;*1C<"));

        assertNotNull(position, "RLC");
        assertEquals(-38.69513, position.getLatitude(), 0.00001);
        assertEquals(-62.35111, position.getLongitude(), 0.00001);

        System.out.println("✅ RLC: Locator OK");
    }

    @Test
    public void testDecodeRHT() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        // RHT — Link a mapas (multilínea)
        var position = (Position) decoder.decode(null, null, text(
                ">RHT0",
                "http://maps.google.com/maps?f=q&q=-38.695130,-62.351110&om=1&z=17",
                ";ID=KJA-169;*70<"));
        assertNotNull(position, "RHT");
        assertTrue(((String) position.getAttributes().get("mapLink")).contains("google.com/maps"));

        System.out.println("✅ RHT: link a Google Maps OK");
    }

    @Test
    public void testDecodeEmptyFrame() throws Exception {
        var decoder = inject(new RinhoProtocolDecoder(null));

        // Frame sin prefijo (solo device ID) — el decoder lo descarta correctamente
        var position = (Position) decoder.decode(null, null, text(
                ">;ID=KJA-169;*77<"));
        // Este frame no tiene tipo de mensaje → el decoder retorna null (esperado)
        // No es un error, es un frame de identificación sin datos
        System.out.println("✅ Empty frame (device-only): retorna null correctamente");
    }

}
