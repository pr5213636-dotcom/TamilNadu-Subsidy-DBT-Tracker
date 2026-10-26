Tamil Nadu Subsidy & DBT Tracker System

A Java and MySQL-based application designed to manage government subsidy schemes, beneficiary details, applications, and Direct Benefit Transfer (DBT) disbursement information.

📌 Project Overview

The Tamil Nadu Subsidy & DBT Tracker System aims to organize subsidy-related information in a centralized database. It stores beneficiary records, government departments, subsidy schemes, and application statuses to support efficient subsidy management.

🎯 Objectives

- Maintain beneficiary information.
- Store government department details.
- Manage subsidy schemes and grant amounts.
- Track beneficiary applications and their current status.
- Record disbursed amounts and officer remarks.
- Maintain scheme budget and allocation information.

✨ Key Features

- Beneficiary Management: Store beneficiary details, district, annual income, and beneficiary type.
- Department Management: Maintain department names, nodal officers, and contact emails.
- Scheme Management: Store scheme types, grant amounts, total budgets, and allocated budgets.
- Application Tracking: Record application status, submission date, and officer remarks.
- DBT Tracking: Maintain the amount disbursed for each application.
- Relational Database: Use primary keys and foreign keys to maintain data relationships.

🛠️ Technologies Used

- Programming Language: Java
- Database: MySQL
- Database Connectivity: JDBC (Java Database Connectivity)
- Database Tool: MySQL Workbench
- Code Editor: Visual Studio Code
- Version Control: Git
- Repository Hosting: GitHub

🗄️ Database Structure

The project database contains four main tables.

1. "departments"

Stores government department information.

- "dept_id" — Primary key
- "dept_name" — Department name
- "nodal_officer" — Nodal officer's name
- "contact_email" — Department contact email

2. "beneficiaries"

Stores information about individuals registered for subsidy schemes.

- "aadhaar_number" — Primary key
- "full_name" — Beneficiary's full name
- "district" — District
- "annual_income" — Annual income
- "contact_number" — Contact number
- "beneficiary_type" — Type of beneficiary
- "param_1" and "param_2" — Additional scheme-specific details

3. "schemes"

Stores subsidy scheme and budget information.

- "scheme_id" — Primary key
- "scheme_name" — Scheme name
- "scheme_type" — Type of scheme
- "dept_id" — Foreign key referencing "departments"
- "grant_amount" — Grant amount
- "total_budget" — Total budget
- "allocated_budget" — Allocated budget

4. "applications"

Stores applications submitted by beneficiaries.

- "application_id" — Primary key
- "aadhaar_number" — Foreign key referencing "beneficiaries"
- "scheme_id" — Foreign key referencing "schemes"
- "current_status" — Current application status
- "disbursed_amount" — Amount disbursed
- "officer_remarks" — Officer comments
- "created_at" — Application creation timestamp

🔗 Database Relationships

- One department can manage multiple subsidy schemes.
- One beneficiary can have multiple applications.
- One scheme can receive multiple applications.
- Each application references one beneficiary and one scheme.

⚙️ Installation and Setup

Prerequisites

Install the following software:

1. Java Development Kit (JDK).
2. MySQL Server.
3. MySQL Workbench (optional, for database management).
4. Visual Studio Code.
5. MySQL Connector/J JDBC driver.

Step 1: Clone the Repository

git clone YOUR_GITHUB_REPOSITORY_URL
cd YOUR_PROJECT_FOLDER

Replace the placeholders with your actual GitHub repository URL and project folder name.

Step 2: Create the Database

Open MySQL Workbench and execute:

CREATE DATABASE subsidy_tracker;
USE subsidy_tracker;

Step 3: Create the Tables

Open the project's SQL file in MySQL Workbench and execute the table creation statements.

Ensure that the tables are created successfully before running the Java application.

Step 4: Configure JDBC

Update the JDBC connection settings in your Java source code.

Example:

String url = "jdbc:mysql://localhost:3306/subsidy_tracker";
String username = "root";
String password = "YOUR_MYSQL_PASSWORD";

Replace "YOUR_MYSQL_PASSWORD" with your actual MySQL password. Do not upload real passwords or database credentials to GitHub.

Step 5: Run the Application

Open the project folder in Visual Studio Code, ensure the MySQL JDBC driver is configured, and compile and run the Java application using the appropriate classpath and main class.

🔒 Security and Privacy

Beneficiary records may contain sensitive personal information.

- Do not upload real Aadhaar numbers, personal contact details, or confidential beneficiary data.
- Do not commit database passwords or other secrets.
- Use sample data for demonstrations.
- Restrict access to personal information and protect database credentials.

🚀 Future Enhancements

- Secure user login and role-based access.
- Beneficiary eligibility verification.
- Advanced search and filtering.
- Application approval and rejection workflows.
- Reports and charts for scheme budgets and disbursements.
- Improved validation and error handling.
- Secure integration with authorized DBT services, if available.

👨‍💻 Project Information

Project Name: Tamil Nadu Subsidy & DBT Tracker System
Project Type: Academic Software Project
Backend: Java
Database: MySQL
Connectivity: JDBC
Development Environment: Visual Studio Code

📄 License

This project is intended for educational and demonstration purposes. Add an appropriate open-source license if you want others to reuse or distribute the code.
