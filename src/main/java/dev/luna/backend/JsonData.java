package dev.luna.backend;
import com.google.gson.*;import java.util.*;
public final class JsonData {
 public static final Gson GSON=new GsonBuilder().serializeNulls().create();private JsonData(){}
 public static JsonObject o(){return new JsonObject();}public static JsonArray a(){return new JsonArray();}
 public static String s(JsonObject j,String k,String fallback){return j.has(k)&&!j.get(k).isJsonNull()?j.get(k).getAsString():fallback;}
 public static boolean b(JsonObject j,String k){return j.has(k)&&j.get(k).isJsonPrimitive()&&j.get(k).getAsJsonPrimitive().isBoolean()&&j.get(k).getAsBoolean();}
 public static int n(JsonElement v,int min,int max){try{if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();int i=v.getAsBigDecimal().intValueExact();if(i<min||i>max)throw new IllegalArgumentException();return i;}catch(Exception e){throw new IllegalArgumentException("Integer outside "+min+".."+max);}}
 public static int i(JsonObject j,String k,int fallback){return j.has(k)?n(j.get(k),Integer.MIN_VALUE,Integer.MAX_VALUE):fallback;}
 public static int[] p(JsonElement el){if(el==null||!el.isJsonArray()||el.getAsJsonArray().size()!=3)throw new IllegalArgumentException("Three coordinates required");JsonArray a=el.getAsJsonArray();return new int[]{n(a.get(0),-30000000,30000000),n(a.get(1),-30000000,30000000),n(a.get(2),-30000000,30000000)};}
 public static JsonArray p(int[] values){JsonArray a=a();for(int v:values)a.add(v);return a;}
 public static String key(int[] p){return p[0]+","+p[1]+","+p[2];}
 public static boolean same(String actual,String expected){if(actual==null||expected==null)return false;String id=expected.split("\\[")[0];if(!actual.split("\\[")[0].equals(id))return false;if(!expected.contains("["))return true;if(!actual.contains("["))return false;Set<String> values=new HashSet<>(Arrays.asList(actual.substring(actual.indexOf('[')+1,actual.length()-1).split(",")));return values.containsAll(Arrays.asList(expected.substring(expected.indexOf('[')+1,expected.length()-1).split(",")));}
 public static void require(boolean b,String error){if(!b)throw new IllegalArgumentException(error);}
}
