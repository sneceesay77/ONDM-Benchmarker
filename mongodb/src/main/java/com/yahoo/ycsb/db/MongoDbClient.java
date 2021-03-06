package com.yahoo.ycsb.db;

/**
 * MongoDB client binding for YCSB.
 *
 * Submitted by Yen Pai on 5/11/2010.
 *
 * https://gist.github.com/000a66b8db2caf42467b#file_mongo_db.java
 *
 */

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Map;
import java.util.Vector;

import org.fluttercode.datafactory.impl.DataFactory;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBAddress;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;
import com.yahoo.ycsb.Status;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.StringByteIterator;

/**
 * MongoDB client for YCSB framework.
 * 
 * Properties to set:
 * 
 * mongodb.url=mongodb://localhost:27017 mongodb.database=ycsb
 * mongodb.writeConcern=normal
 * 
 * @author ypai
 * 
 */
public class MongoDbClient extends DB
{

    private static Mongo mongo;

    private WriteConcern writeConcern;

    private String database;

    private static com.mongodb.DB db = null;

    private static DataFactory df = new DataFactory();
    
    // private DBCollection collection;

    @Override
    /**
     * Initialize any state for this DB.
     * Called once per DB instance; there is one DB instance per client thread.
     */
    public void init() throws DBException
    {
        // initialize MongoDb driver
        Properties props = getProperties();
        
        // default localhost / mechelen.labo1.cs.kuleuven.be
        String host = props.getProperty("hosts", "localhost");
        String port = props.getProperty("port", "27017");
        String url = props.getProperty("mongodb.url",
                "mongodb://localhost:27017");
//        String url = props.getProperty("mongodb.url",
//                "mongodb://mechelen.labo1.cs.kuleuven.be:27017");
        database = props.getProperty("schema", "mongodb");
        
        String writeConcernType = props.getProperty("mongodb.writeConcern", "acknowledged").toLowerCase();

        if ("acknowledged".equals(writeConcernType))
        {
            writeConcern = WriteConcern.ACKNOWLEDGED;
        }
        else if ("safe".equals(writeConcernType))
        {
            writeConcern = WriteConcern.SAFE;
        }
        else if ("normal".equals(writeConcernType))
        {
            writeConcern = WriteConcern.NORMAL;
        }
        else if ("fsync_safe".equals(writeConcernType))
        {
            writeConcern = WriteConcern.FSYNC_SAFE;
        }
        else if ("replicas_safe".equals(writeConcernType))
        {
            writeConcern = WriteConcern.REPLICAS_SAFE;
        }
        else
        {
            System.err.println("ERROR: Invalid writeConcern: '" + writeConcernType + "'. "
                    + "Must be [ none | safe | normal | fsync_safe | replicas_safe ]");
            System.exit(1);
        }

        synchronized (host)
        {
            if (mongo == null)
            {
                try {
					mongo = new MongoClient(host);
					mongo.setWriteConcern(writeConcern);
					
				} catch (UnknownHostException e) {
					// Host error etc.
					e.printStackTrace();
				}
                db = mongo.getDB(database);
            }
        }
    }

    @Override
    /**
     * Cleanup any state for this DB.
     * Called once per DB instance; there is one DB instance per client thread.
     */
    public void cleanup() throws DBException
    {
       /* try
        {
            mongo.close();
        }
        catch (Exception e1)
        {
            System.err.println("Could not close MongoDB connection pool: " + e1.toString());
            e1.printStackTrace();
            return;
        }*/
    }

    @Override
    /**
     * Delete a record from the database.
     *
     * @param table The name of the table
     * @param key The record key of the record to delete.
     * @return Zero on success, a non-zero error code on error. See this class's description for a discussion of error codes.
     */
    public Status delete(String table, String key)
    {
        com.mongodb.DB db = null;
        try
        {
            db = mongo.getDB(database);
            db.requestStart();
            DBCollection collection = db.getCollection(table);
            DBObject q = new BasicDBObject().append("_id", key);
            WriteResult res = collection.remove(q, writeConcern);
            return res.getN() == 1 ? Status.OK: Status.ERROR;
        }
        catch (Exception e)
        {
            System.err.println(e.toString());
            return Status.ERROR;
        }
        finally
        {
            if (db != null)
            {
                db.requestDone();
            }
        }
    }

    @Override
    /**
     * Insert a record in the database. Any field/value pairs in the specified values HashMap will be written into the record with the specified
     * record key.
     *
     * @param table The name of the table
     * @param key The record key of the record to insert.
     * @param values A HashMap of field/value pairs to insert in the record
     * @return Zero on success, a non-zero error code on error. See this class's description for a discussion of error codes.
     */
    public Status insert(String table, String key, HashMap<String, ByteIterator> values)
    {
        try
        {
        	df = DataFactory.create(getSeed(key));
        	
            DBCollection collection = db.getCollection(table);
            DBObject r = new BasicDBObject();
            r.put("_id", key);
            
            r.put("firstName", df.getFirstName());
            r.put("lastName", df.getLastName());
            r.put("birthDate", df.getNumberBetween(1990, 2015));
            r.put("company", df.getBusinessName());
            r.put("city", df.getCity());
            r.put("address", df.getAddress());
            r.put("email", df.getEmailAddress());
            r.put("streetname", df.getStreetName());
            r.put("streetsuffix", df.getStreetSuffix());
            r.put("personalnumber", df.getNumberBetween(0, Integer.MAX_VALUE));
            
            collection.insert(r);
            
            return Status.OK;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return Status.ERROR;
        }
    }
    
    /**
	 * Converts a random string into a long.
	 * @param key
	 * @return
	 */
	private long getSeed(String key) {
		String seed = "";
		
		for (int i=0;i< key.length();i++)
		{
			if(Character.isDigit(key.charAt(i)))
		      seed = seed + "" + key.charAt(i);
		}   
		
		return Long.parseLong(seed);
	}

    @Override
    @SuppressWarnings("unchecked")
    /**
     * Read a record from the database. Each field/value pair from the result will be stored in a HashMap.
     *
     * @param table The name of the table
     * @param key The record key of the record to read.
     * @param fields The list of fields to read, or null for all of them
     * @param result A HashMap of field/value pairs for the result
     * @return Zero on success, a non-zero error code on error or "not found".
     */
    public Status read(String table, String key, Set<String> fields, HashMap<String, ByteIterator> result)
    {
    	String readType = getProperties().getProperty("readType", "0");
		
		df = DataFactory.create(getSeed(key));
		
		Person person = new Person(key, df.getFirstName(), df.getLastName(), 
				df.getNumberBetween(1990, 2015), df.getBusinessName(), df.getCity(), 
				df.getAddress(), df.getEmailAddress(), df.getStreetName(), 
				df.getStreetSuffix(), df.getNumberBetween(0, Integer.MAX_VALUE));  
		
        try
        {
        	DBObject q = null;
        	
        	if(readType.equals("0") || readType.equals("SELECT-PRIMARY")) {
        		q = new BasicDBObject().append("_id", key);
        	} else if(readType.equals("EMAIL")) {
        		q = new BasicDBObject().append("email", person.getEmail());
        	} else if(readType.equals("OR")) {
        		DBObject clause1 = new BasicDBObject("firstName", person.getFirstName());  
        		DBObject clause2 = new BasicDBObject("lastName", person.getLastName());    
        		
        		BasicDBList or = new BasicDBList();
        		or.add(clause1);
        		or.add(clause2);
        		q = new BasicDBObject("$or", or);
        	} else if(readType.equals("AND")) {
        		DBObject clause1 = new BasicDBObject("email", person.getEmail());  
        		DBObject clause2 = new BasicDBObject("personalnumber", person.getPersonalnumber());    
        		
        		BasicDBList or = new BasicDBList();
        		or.add(clause1);
        		or.add(clause2);
        		q = new BasicDBObject("$and", or);
        	} else if(readType.equals("BETWEEN")) {
        		BasicDBObject clause1 = new BasicDBObject("$gt", person.getPersonalnumber() - 100000);
        		clause1.append("$lt", person.getPersonalnumber() + 100000);
        		
        		q= new BasicDBObject("personalnumber", clause1); 
        	}
        	
            DBCollection collection = db.getCollection(table);
            
            List<DBObject> results = collection.find(q).limit(100000).toArray();
            
//            System.out.println(key);
//            System.out.println(results.size());
//            System.out.println(q);
//            System.out.println(results);
            
            if(results.contains(person)) {
            	return Status.OK;
            }
            
            throw new IllegalArgumentException("Invalid id");
        }
        catch (Exception e)
        {
            System.err.println(e.toString());
            return Status.ERROR;
        }
        /*
         * finally { if (db != null) { db.requestDone(); } }
         */
    }

    @Override
    /**
     * Update a record in the database. Any field/value pairs in the specified values HashMap will be written into the record with the specified
     * record key, overwriting any existing values with the same field name.
     *
     * @param table The name of the table
     * @param key The record key of the record to write.
     * @param values A HashMap of field/value pairs to update in the record
     * @return Zero on success, a non-zero error code on error. See this class's description for a discussion of error codes.
     */
    public Status update(String table, String key, HashMap<String, ByteIterator> values)
    {
        try
        {
            DBCollection collection = db.getCollection(table);
            DBObject q = new BasicDBObject().append("_id", key);
            DBObject u = new BasicDBObject();
            DBObject fieldsToSet = new BasicDBObject();
            Iterator<String> keys = values.keySet().iterator();
            while (keys.hasNext())
            {
                String tmpKey = keys.next();
                fieldsToSet.put(tmpKey, values.get(tmpKey).toArray());
            }
            u.put("$set", fieldsToSet);
            WriteResult res = collection.update(q, u, false, false, writeConcern);
            return res.getN() == 1 ? Status.OK : Status.ERROR;
        }
        catch (Exception e)
        {
            System.err.println(e.toString());
            return Status.ERROR;
        }
        finally
        {
            if (db != null)
            {
                db.requestDone();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * Perform a range scan for a set of records in the database. Each field/value pair from the result will be stored in a HashMap.
     *
     * @param table The name of the table
     * @param startkey The record key of the first record to read.
     * @param recordcount The number of records to read
     * @param fields The list of fields to read, or null for all of them
     * @param result A Vector of HashMaps, where each HashMap is a set field/value pairs for one record
     * @return Zero on success, a non-zero error code on error. See this class's description for a discussion of error codes.
     */
    public Status scan(String table, String startkey, int recordcount, Set<String> fields,
            Vector<HashMap<String, ByteIterator>> result)
    {
        com.mongodb.DB db = null;
        try
        {
            db = mongo.getDB(database);
            db.requestStart();
            DBCollection collection = db.getCollection(table);
            // { "_id":{"$gte":startKey, "$lte":{"appId":key+"\uFFFF"}} }
            DBObject scanRange = new BasicDBObject().append("$gte", startkey);
            DBObject q = new BasicDBObject().append("_id", scanRange);
            DBCursor cursor = collection.find(q).limit(recordcount);
            while (cursor.hasNext())
            {
                // toMap() returns a Map, but result.add() expects a
                // Map<String,String>. Hence, the suppress warnings.
                result.add(StringByteIterator.getByteIteratorMap((Map<String, String>) cursor.next().toMap()));
            }

            return Status.OK;
        }
        catch (Exception e)
        {
            System.err.println(e.toString());
            return Status.ERROR;
        }
        finally
        {
            if (db != null)
            {
                db.requestDone();
            }
        }

    }

    private String getString(String key, String value)
    {
        StringBuilder builder = new StringBuilder(key);
        builder.append(value);
        return builder.toString();
    }
}