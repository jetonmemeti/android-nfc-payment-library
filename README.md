android-nfc-payment-library
===========================

This is an Android library designed for two-way NFC payments whith Android KitKat devices and the <a href="http://www.acs.com.hk/en/products/3/acr122u-usb-nfc-reader/">ACR122u USB NFC Reader</a>.


This library requires that you have successfully installed the <a href="https://github.com/jetonmemeti/android-kitkat-nfc-library">AndroidKitKatNFCLibrary</a> as well as the <a href="https://github.com/jetonmemeti/custom-serialization">custom-serialization</a> library.

Prerequisites:
--------------
See the <i>Prerequisites</i> section in the <i>Readme</i> of the <a href="https://github.com/jetonmemeti/android-kitkat-nfc-library">AndroidKitKatNFCLibrary</a>.<br>

Installation Guidelines:
------------------------
The installation of this library is similar to the installation of the <a href="https://github.com/jetonmemeti/android-kitkat-nfc-library">AndroidKitKatNFCLibrary</a>.

How to Use:
-----------
You can have a look at or check out the <a href="https://github.com/jetonmemeti/SamplePaymentProject">SamplePaymentProject</a> to see how you can use this library in your Android application.

If you want to import this library into your own project, there are three important things that you need to add to your project in order for the NFC to work properly. This is also done in the <a href="https://github.com/jetonmemeti/SamplePaymentProject">SamplePaymentProject</a>.
<ul>
  <li>Copy the file <a href="https://github.com/jetonmemeti/android-kitkat-nfc-library/blob/master/apduservice.xml">apduservice.xml</a> to <code>&lt;project root folder&gt;\res\xml\</code>.</li>
  <li>In <code>&lt;project root folder&gt;\res\values\strings.xml</code> add the following:<br>
    <pre><code>&lt;!-- APDU SERVICE --&gt;</code><br>
    <code>&lt;string name="aiddescription"&gt;ch.uzh.csg.nfclib&lt;/string&gt;</code><br>
    <code>&lt;string name="servicedesc"&gt;Android KitKat NFC Library&lt;/string&gt;</code></pre>
  </li>
  <li>In the <code>AndroidManifest.xml</code> add the following:<br>
    <pre><code>&lt;uses-sdk android:minSdkVersion="19" android:targetSdkVersion="19" /&gt;</code><br>  
    <code>&lt;uses-feature android:name="android.hardware.nfc" android:required="true" /&gt;</code><br>  
    <code>&lt;uses-permission android:name="android.permission.NFC" /&gt;</code></pre>
    Inside the <code>&lt;application&gt;</code> tag add:<br>
    <pre><code>&lt;service android:name="ch.uzh.csg.nfclib.CustomHostApduService" android:exported="true" android:permission="android.permission.BIND_NFC_SERVICE"&gt;<br>
  &lt;intent-filter&gt;<br>
    &lt;action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE" /&gt;<br>
  &lt;/intent-filter&gt;<br>
  &lt;meta-data android:name="android.nfc.cardemulation.host_apdu_service" android:resource="@xml/apduservice" /&gt;<br>
&lt;/service&gt;</code></pre>
  </li>
</ul>

Once this is done, you can use this library in your project by adding its <i>groupId</i>, <i>artifactId</i>, and <i>version</i> (see pom.xml) to the POM of your project.
