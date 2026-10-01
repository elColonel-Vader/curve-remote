// SPDX-License-Identifier: Apache-2.0
package javax.microedition.rms;
public class RecordStore {
 private static byte[] record;
 public static RecordStore openRecordStore(String name,boolean create){return new RecordStore();}
 public int getNumRecords(){return record==null?0:1;} public byte[] getRecord(int id){return record;}
 public int addRecord(byte[] data,int offset,int length){setRecord(1,data,offset,length);return 1;}
 public void setRecord(int id,byte[] data,int offset,int length){record=new byte[length];System.arraycopy(data,offset,record,0,length);}
 public void closeRecordStore(){} public static void reset(){record=null;}public static void legacy(byte[] data){record=data;}
}
