package org.omg.IOP;


/**
* org/omg/IOP/CodecOperations.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /Users/java_re/workspace/8-2-build-macosx-x86_64/jdk8u121/8372/corba/src/share/classes/org/omg/PortableInterceptor/IOP.idl
* Monday, December 12, 2016 8:41:13 PM PST
*/


/**
   * The formats of IOR components and service context data used by ORB 
   * services are often defined as CDR encapsulations encoding instances 
   * of IDL defined data types. The <code>Codec</code> provides a mechanism 
   * to transfer these components between their IDL data types and their CDR 
   * encapsulation representations. 
   * <p>
   * A <code>Codec</code> is obtained from the <code>CodecFactory</code>. 
   * The <code>CodecFactory</code> is obtained through a call to 
   * <code>ORB.resolve_initial_references( "CodecFactory" )</code>.
   */
public interface CodecOperations 
{

  /**
       * Converts the given any into a byte array based on the encoding 
       * format effective for this <code>Codec</code>. 
       *
       * @param data The data, in the form of an any, to be encoded into 
       *     a byte array.
       * @return A byte array containing the encoded Any. This byte array 
       *     contains both the <code>TypeCode</code> and the data of the type.
       * @exception InvalidTypeForEncoding thrown if the type is not valid for 
       *     the encoding format effective for this <code>Codec</code>.
       */
  byte[] encode (org.omg.CORBA.Any data) throws org.omg.IOP.CodecPackage.InvalidTypeForEncoding;

  /**
       * Decodes the given byte array into an Any based on the encoding 
       * format effective for this <code>Codec</code>. 
       * 
       * @param data The data, in the form of a byte array, to be decoded into 
       *     an Any. 
       * @return An Any containing the data from the decoded byte array.
       * @exception FormatMismatch is thrown if the byte array cannot be 
       *     decoded into an Any. 
       */
  org.omg.CORBA.Any decode (byte[] data) throws org.omg.IOP.CodecPackage.FormatMismatch;

  /**
       * Converts the given any into a byte array based on the encoding 
       * format effective for this Codec. Only the data from the Any is 
       * encoded, not the <code>TypeCode</code>. 
       *
       * @param data The data, in the form of an Any, to be encoded into 
       *     a byte array. 
       * @return A byte array containing the data from the encoded any.
       * @exception InvalidTypeForEncoding thrown if the type is not valid for 
       *     the encoding format effective for this <code>Codec</code>.
       */
  byte[] encode_value (org.omg.CORBA.Any data) throws org.omg.IOP.CodecPackage.InvalidTypeForEncoding;

  /**
       * Decodes the given byte array into an Any based on the given 
       * <code>TypeCode</code> and the encoding format effective for 
       * this <code>Codec</code>. 
       *
       * @param data The data, in the form of a byte array, to be decoded 
       *     into an Any. 
       * @param tc The TypeCode to be used to decode the data. 
       * @return An Any containing the data from the decoded byte array.
       * @exception FormatMismatch thrown if the byte array cannot be 
       *     decoded into an Any. 
       */
  org.omg.CORBA.Any decode_value (byte[] data, org.omg.CORBA.TypeCode tc) throws org.omg.IOP.CodecPackage.FormatMismatch, org.omg.IOP.CodecPackage.TypeMismatch;
} // interface CodecOperations
