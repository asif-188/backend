package com.globalisor.backend;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class DbCheck {
    public static void main(String[] args) {
        String uri = "mongodb+srv://greenbridge_db_user:Pass%40green@globalisor.m4zuxpm.mongodb.net/globalisor?appName=globalisor";
        try (MongoClient mongoClient = MongoClients.create(uri)) {
            System.out.println("=== Connected to MongoDB ===");
            for (String dbName : mongoClient.listDatabaseNames()) {
                System.out.println("DB: " + dbName);
            }
            MongoDatabase db = mongoClient.getDatabase("globalisor");
            System.out.println("Collections in globalisor:");
            for (String colName : db.listCollectionNames()) {
                long count = db.getCollection(colName).countDocuments();
                System.out.println(" - " + colName + ": " + count + " docs");
            }
            System.out.println("--- All Users ---");
            for (Document doc : db.getCollection("users").find()) {
                System.out.println("User: id=" + doc.get("_id") + ", email=" + doc.get("email") + ", role=" + doc.get("role") + ", status=" + doc.get("status") + ", name=" + doc.get("firstName") + " " + doc.get("lastName"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
