USE subsidy_tracker;

CREATE TABLE IF NOT EXISTS departments (
    dept_id VARCHAR(50) PRIMARY KEY,
    dept_name VARCHAR(150) NOT NULL,
    nodal_officer VARCHAR(100) NOT NULL,
    contact_email VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS beneficiaries (
    aadhaar_number VARCHAR(12) PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    district VARCHAR(50) NOT NULL,
    annual_income DOUBLE PRECISION NOT NULL,
    contact_number VARCHAR(15) NOT NULL,
    beneficiary_type VARCHAR(50) NOT NULL,
    param_1 VARCHAR(100),
    param_2 VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS schemes (
    scheme_id VARCHAR(50) PRIMARY KEY,
    scheme_name VARCHAR(200) NOT NULL,
    scheme_type VARCHAR(100) NOT NULL,
    dept_id VARCHAR(50) NOT NULL,
    grant_amount DOUBLE PRECISION NOT NULL,
    total_budget DOUBLE PRECISION NOT NULL,
    allocated_budget DOUBLE PRECISION DEFAULT 0.0,
    FOREIGN KEY (dept_id)
        REFERENCES departments(dept_id)
);

CREATE TABLE IF NOT EXISTS applications (
    application_id VARCHAR(50) PRIMARY KEY,
    aadhaar_number VARCHAR(12) NOT NULL,
    scheme_id VARCHAR(50) NOT NULL,
    current_status VARCHAR(30) NOT NULL,
    disbursed_amount DOUBLE PRECISION DEFAULT 0.0,
    officer_remarks TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (aadhaar_number)
        REFERENCES beneficiaries(aadhaar_number),
    FOREIGN KEY (scheme_id)
        REFERENCES schemes(scheme_id)
);

SHOW TABLES;