import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/* =========================================================================
 * 0. MAIN ENTRY POINT (Supports both --gui and --cli modes)
 * ========================================================================= */
public class SubsidyBeneficiaryTracker {
    public static void main(String[] args) {
        if (args.length > 0 && "--cli".equalsIgnoreCase(args[0])) {
            new ConsoleApp().start();
        } else {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            SwingUtilities.invokeLater(() -> {
                MainGuiWindow window = new MainGuiWindow();
                window.setVisible(true);
            });
        }
    }
}

/* =========================================================================
 * 1. CUSTOM EXCEPTIONS
 * ========================================================================= */
class InvalidAadhaarException extends Exception {
    public InvalidAadhaarException(String message) {
        super(message);
    }
}

class IneligibleBeneficiaryException extends Exception {
    public IneligibleBeneficiaryException(String message) {
        super(message);
    }
}

class DuplicateApplicationException extends Exception {
    public DuplicateApplicationException(String message) {
        super(message);
    }
}

class ApplicationWorkflowException extends RuntimeException {
    public ApplicationWorkflowException(String message) {
        super(message);
    }
}

/* =========================================================================
 * 2. OBJECT-ORIENTED PROGRAMMING: BENEFICIARY HIERARCHY
 * ========================================================================= */
abstract class Beneficiary {
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("^\\d{12}$");

    private final String aadhaarNumber;
    private String fullName;
    private String district;
    private double annualIncome;
    private String contactNumber;

    public Beneficiary(String aadhaarNumber, String fullName, String district, 
                       double annualIncome, String contactNumber) throws InvalidAadhaarException {
        if (aadhaarNumber == null || !AADHAAR_PATTERN.matcher(aadhaarNumber).matches()) {
            throw new InvalidAadhaarException("Aadhaar must be an exact 12-digit numeric identity. Input: " + aadhaarNumber);
        }
        if (annualIncome < 0) {
            throw new IllegalArgumentException("Annual income cannot be negative.");
        }
        this.aadhaarNumber = aadhaarNumber;
        this.fullName = fullName;
        this.district = district;
        this.annualIncome = annualIncome;
        this.contactNumber = contactNumber;
    }

    public abstract String getBeneficiaryType();
    public abstract String getSpecificCriteriaSummary();

    public String getAadhaarNumber() { return aadhaarNumber; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getDistrict() { return district; }
    public double getAnnualIncome() { return annualIncome; }
    public String getContactNumber() { return contactNumber; }
}

class FarmerBeneficiary extends Beneficiary {
    private double landHoldingAcres;
    private String cropType;

    public FarmerBeneficiary(String aadhaar, String name, String district, double income, 
                             String phone, double landAcres, String cropType) throws InvalidAadhaarException {
        super(aadhaar, name, district, income, phone);
        this.landHoldingAcres = landAcres;
        this.cropType = cropType;
    }

    public double getLandHoldingAcres() { return landHoldingAcres; }
    public String getCropType() { return cropType; }

    @Override public String getBeneficiaryType() { return "FARMER"; }
    @Override public String getSpecificCriteriaSummary() {
        return "Land: " + landHoldingAcres + " Acres | Crop: " + cropType;
    }
}

class SeniorCitizenBeneficiary extends Beneficiary {
    private int age;

    public SeniorCitizenBeneficiary(String aadhaar, String name, String district, double income, 
                                   String phone, int age) throws InvalidAadhaarException {
        super(aadhaar, name, district, income, phone);
        this.age = age;
    }

    public int getAge() { return age; }

    @Override public String getBeneficiaryType() { return "SENIOR_CITIZEN"; }
    @Override public String getSpecificCriteriaSummary() { return "Age: " + age + " years"; }
}

class DifferentlyAbledBeneficiary extends Beneficiary {
    private String udidNumber;
    private int disabilityPercentage;

    public DifferentlyAbledBeneficiary(String aadhaar, String name, String district, double income,
                                       String phone, String udidNumber, int disabilityPercentage) throws InvalidAadhaarException {
        super(aadhaar, name, district, income, phone);
        this.udidNumber = udidNumber;
        this.disabilityPercentage = disabilityPercentage;
    }

    public String getUdidNumber() { return udidNumber; }
    public int getDisabilityPercentage() { return disabilityPercentage; }

    @Override public String getBeneficiaryType() { return "DIFFERENTLY_ABLED"; }
    @Override public String getSpecificCriteriaSummary() {
        return "UDID: " + udidNumber + " | Disability: " + disabilityPercentage + "%";
    }
}

class StudentBeneficiary extends Beneficiary {
    private boolean studiedInGovtSchool6To12;
    private String institutionName;
    private String currentCourse;

    public StudentBeneficiary(String aadhaar, String name, String district, double income, String phone,
                              boolean studiedInGovtSchool, String institution, String course) throws InvalidAadhaarException {
        super(aadhaar, name, district, income, phone);
        this.studiedInGovtSchool6To12 = studiedInGovtSchool;
        this.institutionName = institution;
        this.currentCourse = course;
    }

    public boolean isStudiedInGovtSchool6To12() { return studiedInGovtSchool6To12; }
    public String getInstitutionName() { return institutionName; }
    public String getCurrentCourse() { return currentCourse; }

    @Override public String getBeneficiaryType() { return "STUDENT"; }
    @Override public String getSpecificCriteriaSummary() {
        return "Govt School 6-12th: " + (studiedInGovtSchool6To12 ? "YES" : "NO") + " | " + institutionName + " (" + currentCourse + ")";
    }
}

class WidowBeneficiary extends Beneficiary {
    private String deathCertificateRef;
    private int dependentCount;

    public WidowBeneficiary(String aadhaar, String name, String district, double income, String phone,
                            String deathCertRef, int dependentCount) throws InvalidAadhaarException {
        super(aadhaar, name, district, income, phone);
        this.deathCertificateRef = deathCertRef;
        this.dependentCount = dependentCount;
    }

    public String getDeathCertificateRef() { return deathCertificateRef; }
    public int getDependentCount() { return dependentCount; }

    @Override public String getBeneficiaryType() { return "DESTITUTE_WIDOW"; }
    @Override public String getSpecificCriteriaSummary() {
        return "Death Cert Ref: " + deathCertificateRef + " | Dependents: " + dependentCount;
    }
}

class DestituteLabourerBeneficiary extends Beneficiary {
    private String labourCardNumber;

    public DestituteLabourerBeneficiary(String aadhaar, String name, String district, double income, 
                                        String phone, String labourCard) throws InvalidAadhaarException {
        super(aadhaar, name, district, income, phone);
        this.labourCardNumber = labourCard;
    }

    public String getLabourCardNumber() { return labourCardNumber; }

    @Override public String getBeneficiaryType() { return "DESTITUTE_AGRICULTURAL_LABOURER"; }
    @Override public String getSpecificCriteriaSummary() {
        return "Labour Card: " + labourCardNumber;
    }
}

/* =========================================================================
 * 3. DOMAIN: DEPARTMENT & SCHEME
 * ========================================================================= */
class Department {
    private final String departmentId;
    private final String departmentName;
    private final String nodalOfficer;
    private final String contactEmail;

    public Department(String departmentId, String departmentName, String nodalOfficer, String contactEmail) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.nodalOfficer = nodalOfficer;
        this.contactEmail = contactEmail;
    }

    public String getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }
    public String getNodalOfficer() { return nodalOfficer; }
    public String getContactEmail() { return contactEmail; }
}

abstract class Scheme {
    private final String schemeId;
    private final String schemeName;
    private final Department department;
    private final double standardGrant;
    private final double totalBudget;
    private double allocatedBudget;
    private final EligibilityStrategy eligibilityStrategy;

    public Scheme(String schemeId, String schemeName, Department department, 
                  double standardGrant, double totalBudget, EligibilityStrategy strategy) {
        this.schemeId = schemeId;
        this.schemeName = schemeName;
        this.department = department;
        this.standardGrant = standardGrant;
        this.totalBudget = totalBudget;
        this.allocatedBudget = 0.0;
        this.eligibilityStrategy = strategy;
    }

    public synchronized boolean canAccommodate(double amount) {
        return (totalBudget - allocatedBudget) >= amount;
    }

    public synchronized void reserveFunds(double amount) {
        if (!canAccommodate(amount)) {
            throw new IllegalStateException("Budget exhausted for TN Scheme: " + schemeName);
        }
        this.allocatedBudget += amount;
    }

    public abstract String getSchemeCategory();

    public String getSchemeId() { return schemeId; }
    public String getSchemeName() { return schemeName; }
    public Department getDepartment() { return department; }
    public double getStandardGrant() { return standardGrant; }
    public double getTotalBudget() { return totalBudget; }
    public double getAllocatedBudget() { return allocatedBudget; }
    public EligibilityStrategy getEligibilityStrategy() { return eligibilityStrategy; }
}

/* =========================================================================
 * 4. DESIGN PATTERN: STRATEGY (Eligibility Verification Rules)
 * ========================================================================= */
@FunctionalInterface
interface EligibilityStrategy {
    boolean evaluate(Beneficiary beneficiary, Scheme scheme) throws IneligibleBeneficiaryException;
}

class AgeCriteriaStrategy implements EligibilityStrategy {
    private final int minAgeRequired;
    private final double maxAnnualIncome;

    public AgeCriteriaStrategy(int minAgeRequired, double maxAnnualIncome) {
        this.minAgeRequired = minAgeRequired;
        this.maxAnnualIncome = maxAnnualIncome;
    }

    @Override
    public boolean evaluate(Beneficiary b, Scheme s) throws IneligibleBeneficiaryException {
        if (!(b instanceof SeniorCitizenBeneficiary senior)) {
            throw new IneligibleBeneficiaryException("Scheme requires applicant to be registered as Senior Citizen.");
        }
        if (senior.getAge() < minAgeRequired) {
            throw new IneligibleBeneficiaryException("Age " + senior.getAge() + " is below minimum threshold of " + minAgeRequired + " years.");
        }
        if (senior.getAnnualIncome() > maxAnnualIncome) {
            throw new IneligibleBeneficiaryException("Annual Income ₹" + senior.getAnnualIncome() + " exceeds limit of ₹" + maxAnnualIncome);
        }
        return true;
    }
}

class LandHoldingStrategy implements EligibilityStrategy {
    private final double maxAcresAllowed;

    public LandHoldingStrategy(double maxAcresAllowed) {
        this.maxAcresAllowed = maxAcresAllowed;
    }

    @Override
    public boolean evaluate(Beneficiary b, Scheme s) throws IneligibleBeneficiaryException {
        if (!(b instanceof FarmerBeneficiary farmer)) {
            throw new IneligibleBeneficiaryException("Scheme requires applicant to be a certified Farmer.");
        }
        if (farmer.getLandHoldingAcres() > maxAcresAllowed) {
            throw new IneligibleBeneficiaryException(
                "Landholding " + farmer.getLandHoldingAcres() + " acres exceeds ceiling of " + maxAcresAllowed + " acres.");
        }
        return true;
    }
}

class DisabilityCriteriaStrategy implements EligibilityStrategy {
    private final int minDisabilityPercentage;

    public DisabilityCriteriaStrategy(int minDisabilityPercentage) {
        this.minDisabilityPercentage = minDisabilityPercentage;
    }

    @Override
    public boolean evaluate(Beneficiary b, Scheme s) throws IneligibleBeneficiaryException {
        if (!(b instanceof DifferentlyAbledBeneficiary da)) {
            throw new IneligibleBeneficiaryException("Applicant must possess Differently Abled Welfare Board registration.");
        }
        if (da.getDisabilityPercentage() < minDisabilityPercentage) {
            throw new IneligibleBeneficiaryException("Certified disability " + da.getDisabilityPercentage() + "% is below required " + minDisabilityPercentage + "%.");
        }
        return true;
    }
}

class GovtSchoolStudentStrategy implements EligibilityStrategy {
    @Override
    public boolean evaluate(Beneficiary b, Scheme s) throws IneligibleBeneficiaryException {
        if (!(b instanceof StudentBeneficiary st)) {
            throw new IneligibleBeneficiaryException("Applicant must be a student currently pursuing an undergraduate degree/diploma.");
        }
        if (!st.isStudiedInGovtSchool6To12()) {
            throw new IneligibleBeneficiaryException("Applicant must have studied 6th to 12th standard continuously in Tamil Nadu Government Schools.");
        }
        return true;
    }
}

class HousingPovertyStrategy implements EligibilityStrategy {
    private final double maxIncome;

    public HousingPovertyStrategy(double maxIncome) {
        this.maxIncome = maxIncome;
    }

    @Override
    public boolean evaluate(Beneficiary b, Scheme s) throws IneligibleBeneficiaryException {
        if (b.getAnnualIncome() > maxIncome) {
            throw new IneligibleBeneficiaryException("Annual Income ₹" + b.getAnnualIncome() + " exceeds rural housing threshold of ₹" + maxIncome);
        }
        return true;
    }
}

/* =========================================================================
 * 5. DESIGN PATTERN: FACTORY (Tamil Nadu Schemes Creator)
 * ========================================================================= */
enum SchemeType {
    OLD_AGE_PENSION,
    DESTITUTE_AGRICULTURAL_LABOURER_PENSION,
    WIDOW_PENSION,
    DIFFERENTLY_ABLED_PENSION,
    FARMER_SUBSIDY_SEEDS,
    FARMER_SUBSIDY_EQUIPMENT,
    EDUCATION_PUDHUMAI_PENN,
    HOUSING_KALAIGNAR_KANAVU_ILLAM,
    HEALTH_CMCHIS
}

class SchemeFactory {
    public static Scheme createScheme(SchemeType type, String id, String name, Department dept, double budget) {
        return switch (type) {
            case OLD_AGE_PENSION -> new Scheme(id, name, dept, 1200.0, budget, new AgeCriteriaStrategy(60, 120000.0)) {
                @Override public String getSchemeCategory() { return "SOCIAL_SECURITY_PENSION"; }
            };
            case DESTITUTE_AGRICULTURAL_LABOURER_PENSION -> new Scheme(id, name, dept, 1200.0, budget, (b, s) -> {
                if (!(b instanceof DestituteLabourerBeneficiary)) {
                    throw new IneligibleBeneficiaryException("Applicant must be a registered Destitute Agricultural Labourer.");
                }
                return true;
            }) {
                @Override public String getSchemeCategory() { return "LABOUR_PENSION"; }
            };
            case WIDOW_PENSION -> new Scheme(id, name, dept, 1200.0, budget, (b, s) -> {
                if (!(b instanceof WidowBeneficiary)) {
                    throw new IneligibleBeneficiaryException("Applicant must be registered under Destitute Widow category.");
                }
                return true;
            }) {
                @Override public String getSchemeCategory() { return "WIDOW_ASSISTANCE"; }
            };
            case DIFFERENTLY_ABLED_PENSION -> new Scheme(id, name, dept, 2000.0, budget, new DisabilityCriteriaStrategy(40)) {
                @Override public String getSchemeCategory() { return "SPECIAL_DISABILITY_PENSION"; }
            };
            case FARMER_SUBSIDY_SEEDS -> new Scheme(id, name, dept, 5000.0, budget, new LandHoldingStrategy(5.0)) {
                @Override public String getSchemeCategory() { return "AGRI_INPUT_SUBSIDY"; }
            };
            case FARMER_SUBSIDY_EQUIPMENT -> new Scheme(id, name, dept, 45000.0, budget, new LandHoldingStrategy(5.0)) {
                @Override public String getSchemeCategory() { return "AGRI_MACHINERY_SUBSIDY"; }
            };
            case EDUCATION_PUDHUMAI_PENN -> new Scheme(id, name, dept, 1000.0, budget, new GovtSchoolStudentStrategy()) {
                @Override public String getSchemeCategory() { return "HIGHER_EDUCATION_AID"; }
            };
            case HOUSING_KALAIGNAR_KANAVU_ILLAM -> new Scheme(id, name, dept, 350000.0, budget, new HousingPovertyStrategy(120000.0)) {
                @Override public String getSchemeCategory() { return "RURAL_HOUSING_DBT"; }
            };
            case HEALTH_CMCHIS -> new Scheme(id, name, dept, 500000.0, budget, (b, s) -> {
                if (b.getAnnualIncome() > 120000.0) {
                    throw new IneligibleBeneficiaryException("CMCHIS requires family income to be <= ₹1,20,000.");
                }
                return true;
            }) {
                @Override public String getSchemeCategory() { return "HEALTH_INSURANCE_COVER"; }
            };
        };
    }
}

/* =========================================================================
 * 6. DESIGN PATTERN: STATE (Workflow Transitions)
 * ========================================================================= */
enum ApplicationStatus {
    REGISTERED,
    VERIFIED,
    ALLOCATED,
    DISBURSED,
    REJECTED
}

interface ApplicationState {
    void verify(SubsidyApplication context, String remarks);
    void allocate(SubsidyApplication context, double amount);
    void disburse(SubsidyApplication context);
    void reject(SubsidyApplication context, String reason);
    String getStateName();
}

class RegisteredState implements ApplicationState {
    @Override
    public void verify(SubsidyApplication ctx, String remarks) {
        ctx.setRemarks(remarks);
        ctx.transitionToState(new VerifiedState(), ApplicationStatus.VERIFIED);
    }

    @Override
    public void allocate(SubsidyApplication ctx, double amount) {
        throw new ApplicationWorkflowException("Invalid Transition: Must be VERIFIED by VAO/Tahsildar before budget allocation.");
    }

    @Override
    public void disburse(SubsidyApplication ctx) {
        throw new ApplicationWorkflowException("Invalid Transition: Cannot disburse funds from REGISTERED state.");
    }

    @Override
    public void reject(SubsidyApplication ctx, String reason) {
        ctx.setRemarks(reason);
        ctx.transitionToState(new RejectedState(), ApplicationStatus.REJECTED);
    }

    @Override
    public String getStateName() { return ApplicationStatus.REGISTERED.name(); }
}

class VerifiedState implements ApplicationState {
    @Override
    public void verify(SubsidyApplication ctx, String remarks) {
        throw new ApplicationWorkflowException("Application is already verified by field officer.");
    }

    @Override
    public void allocate(SubsidyApplication ctx, double amount) {
        if (!ctx.getScheme().canAccommodate(amount)) {
            throw new ApplicationWorkflowException("Treasury allocation rejected: Scheme budget exhausted.");
        }
        ctx.getScheme().reserveFunds(amount);
        ctx.setAllocatedAmount(amount);
        ctx.transitionToState(new AllocatedState(), ApplicationStatus.ALLOCATED);
    }

    @Override
    public void disburse(SubsidyApplication ctx) {
        throw new ApplicationWorkflowException("Funds must be formally sanctioned (ALLOCATED) prior to DBT disbursement.");
    }

    @Override
    public void reject(SubsidyApplication ctx, String reason) {
        ctx.setRemarks(reason);
        ctx.transitionToState(new RejectedState(), ApplicationStatus.REJECTED);
    }

    @Override
    public String getStateName() { return ApplicationStatus.VERIFIED.name(); }
}

class AllocatedState implements ApplicationState {
    @Override public void verify(SubsidyApplication c, String r) { throw new ApplicationWorkflowException("Already verified."); }
    @Override public void allocate(SubsidyApplication c, double a) { throw new ApplicationWorkflowException("Funds already allocated."); }

    @Override
    public void disburse(SubsidyApplication ctx) {
        ctx.transitionToState(new DisbursedState(), ApplicationStatus.DISBURSED);
    }

    @Override
    public void reject(SubsidyApplication ctx, String reason) {
        ctx.setRemarks(reason);
        ctx.transitionToState(new RejectedState(), ApplicationStatus.REJECTED);
    }

    @Override
    public String getStateName() { return ApplicationStatus.ALLOCATED.name(); }
}

class DisbursedState implements ApplicationState {
    @Override public void verify(SubsidyApplication c, String r) { throw new ApplicationWorkflowException("Final state reached: DISBURSED."); }
    @Override public void allocate(SubsidyApplication c, double a) { throw new ApplicationWorkflowException("Final state reached: DISBURSED."); }
    @Override public void disburse(SubsidyApplication c) { throw new ApplicationWorkflowException("Funds already transferred through direct benefit transfer."); }
    @Override public void reject(SubsidyApplication c, String r) { throw new ApplicationWorkflowException("Cannot reject settled disbursement."); }
    @Override public String getStateName() { return ApplicationStatus.DISBURSED.name(); }
}

class RejectedState implements ApplicationState {
    @Override public void verify(SubsidyApplication c, String r) { throw new ApplicationWorkflowException("Application is permanently REJECTED."); }
    @Override public void allocate(SubsidyApplication c, double a) { throw new ApplicationWorkflowException("Application is REJECTED."); }
    @Override public void disburse(SubsidyApplication c) { throw new ApplicationWorkflowException("Application is REJECTED."); }
    @Override public void reject(SubsidyApplication c, String r) { throw new ApplicationWorkflowException("Application already rejected."); }
    @Override public String getStateName() { return ApplicationStatus.REJECTED.name(); }
}

/* =========================================================================
 * 7. DESIGN PATTERN: OBSERVER (Notifications & File Audit Trail)
 * ========================================================================= */
interface ApplicationObserver {
    void onStatusChanged(SubsidyApplication app, ApplicationStatus oldStatus, ApplicationStatus newStatus);
}

class SmsNotificationObserver implements ApplicationObserver {
    @Override
    public void onStatusChanged(SubsidyApplication app, ApplicationStatus oldStatus, ApplicationStatus newStatus) {
        String phone = app.getBeneficiary().getContactNumber();
        String name = app.getBeneficiary().getFullName();
        String scheme = app.getScheme().getSchemeName();
        System.out.printf("[TN-GOV-SMS-GATEWAY] To: +91-%s | Vanakkam %s, your application %s for '%s' transitioned: %s -> %s%n",
                phone, name, app.getApplicationId(), scheme, oldStatus, newStatus);
    }
}

class AuditTrailObserver implements ApplicationObserver {
    private static final Path AUDIT_LOG = Paths.get("tn_subsidy_audit.log");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public synchronized void onStatusChanged(SubsidyApplication app, ApplicationStatus oldStatus, ApplicationStatus newStatus) {
        String logLine = String.format("[%s] APP: %s | CITIZEN: %s (%s) | SCHEME: %s | STATE: %s -> %s | AMOUNT: ₹%.2f%n",
                LocalDateTime.now().format(FORMATTER),
                app.getApplicationId(),
                app.getBeneficiary().getFullName(),
                app.getBeneficiary().getAadhaarNumber(),
                app.getScheme().getSchemeId(),
                oldStatus,
                newStatus,
                app.getAllocatedAmount());
        try {
            Files.writeString(AUDIT_LOG, logLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Audit File Log failed: " + e.getMessage());
        }
    }
}

class SubsidyApplication {
    private final String applicationId;
    private final Beneficiary beneficiary;
    private final Scheme scheme;
    private ApplicationState state;
    private ApplicationStatus status;
    private double allocatedAmount;
    private String remarks;

    private final List<ApplicationObserver> observers = new CopyOnWriteArrayList<>();

    public SubsidyApplication(String applicationId, Beneficiary beneficiary, Scheme scheme) {
        this.applicationId = applicationId;
        this.beneficiary = beneficiary;
        this.scheme = scheme;
        this.state = new RegisteredState();
        this.status = ApplicationStatus.REGISTERED;
        this.allocatedAmount = 0.0;
        this.remarks = "Application received at e-Sevai / Portal.";
    }

    public void attach(ApplicationObserver o) { observers.add(o); }
    public void detach(ApplicationObserver o) { observers.remove(o); }

    public synchronized void transitionToState(ApplicationState newState, ApplicationStatus newStatus) {
        ApplicationStatus old = this.status;
        this.state = newState;
        this.status = newStatus;
        for (ApplicationObserver observer : observers) {
            observer.onStatusChanged(this, old, newStatus);
        }
    }

    public void verify(String remarks) { state.verify(this, remarks); }
    public void allocate(double amount) { state.allocate(this, amount); }
    public void disburse() { state.disburse(this); }
    public void reject(String reason) { state.reject(this, reason); }

    public String getApplicationId() { return applicationId; }
    public Beneficiary getBeneficiary() { return beneficiary; }
    public Scheme getScheme() { return scheme; }
    public ApplicationStatus getStatus() { return status; }
    public double getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(double amount) { this.allocatedAmount = amount; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

/* =========================================================================
 * 8. DESIGN PATTERN: SINGLETON (JDBC Database Manager)
 * ========================================================================= */
class DatabaseManager {
    private static volatile DatabaseManager instance;
    private Connection connection;

    private static final String DB_URL = "jdbc:mysql://localhost:3306/subsidy_tracker";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "3306";

    private DatabaseManager() {
        try {
            this.connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            initSchema();
        } catch (SQLException e) {
            System.err.println("[DB-WARN] In-memory JDBC connection failed: " + e.getMessage());
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    public Connection getConnection() {
        return this.connection;
    }

    private void initSchema() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS applications (
                application_id VARCHAR(50) PRIMARY KEY,
                aadhaar_number VARCHAR(12) NOT NULL,
                scheme_id VARCHAR(50) NOT NULL,
                current_status VARCHAR(30) NOT NULL,
                disbursed_amount DOUBLE DEFAULT 0.0,
                remarks VARCHAR(255)
            );
        """;
        try (Statement st = connection.createStatement()) {
            st.execute(ddl);
        } catch (SQLException ex) {
            System.err.println("[DB-WARN] Schema creation failed: " + ex.getMessage());
        }
    }
}

/* =========================================================================
 * 9. GENERICS & REPOSITORY LAYER
 * ========================================================================= */
interface GenericRepository<T, ID> {
    void save(T entity);
    Optional<T> findById(ID id);
    Collection<T> findAll();
    boolean deleteById(ID id);
}

class SubsidyApplicationRepository implements GenericRepository<SubsidyApplication, String> {
    private final Map<String, SubsidyApplication> storage = new ConcurrentHashMap<>();
    private final Connection db = DatabaseManager.getInstance().getConnection();

    @Override
    public void save(SubsidyApplication app) {
        storage.put(app.getApplicationId(), app);
        syncToJdbc(app);
    }

    @Override
    public Optional<SubsidyApplication> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Collection<SubsidyApplication> findAll() {
        return Collections.unmodifiableCollection(storage.values());
    }

    @Override
    public boolean deleteById(String id) {
        return storage.remove(id) != null;
    }

    private void syncToJdbc(SubsidyApplication app) {
        if (db == null) return;
        String sql = "MERGE INTO applications (application_id, aadhaar_number, scheme_id, current_status, disbursed_amount, remarks) KEY(application_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, app.getApplicationId());
            ps.setString(2, app.getBeneficiary().getAadhaarNumber());
            ps.setString(3, app.getScheme().getSchemeId());
            ps.setString(4, app.getStatus().name());
            ps.setDouble(5, app.getAllocatedAmount());
            ps.setString(6, app.getRemarks());
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Memory storage holds the source of truth if embedded driver lacks MERGE syntax
        }
    }
}

/* =========================================================================
 * 10. MULTITHREADING: SERVICES (Parallel Verification & CSV Reports)
 * ========================================================================= */
class ParallelVerificationService {
    private final ExecutorService threadPool = Executors.newFixedThreadPool(4);

    public record VerificationOutcome(boolean aadhaarValid, boolean pattaOrRegistryValid, boolean npciBankLinked) {
        public boolean isAllPassed() {
            return aadhaarValid && pattaOrRegistryValid && npciBankLinked;
        }
    }

    public CompletableFuture<VerificationOutcome> performParallelVerificationAsync(Beneficiary beneficiary) {
        CompletableFuture<Boolean> aadhaarCheck = CompletableFuture.supplyAsync(() -> {
            simulateLatency(350);
            return beneficiary.getAadhaarNumber().length() == 12;
        }, threadPool);

        CompletableFuture<Boolean> registryCheck = CompletableFuture.supplyAsync(() -> {
            simulateLatency(500);
            return beneficiary.getDistrict() != null && !beneficiary.getDistrict().isBlank();
        }, threadPool);

        CompletableFuture<Boolean> bankLinkCheck = CompletableFuture.supplyAsync(() -> {
            simulateLatency(400);
            return beneficiary.getContactNumber().startsWith("9") || 
                   beneficiary.getContactNumber().startsWith("8") || 
                   beneficiary.getContactNumber().startsWith("7") ||
                   beneficiary.getContactNumber().startsWith("6");
        }, threadPool);

        return CompletableFuture.allOf(aadhaarCheck, registryCheck, bankLinkCheck)
                .thenApply(v -> new VerificationOutcome(
                        aadhaarCheck.join(),
                        registryCheck.join(),
                        bankLinkCheck.join()
                ));
    }

    private void simulateLatency(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        threadPool.shutdown();
    }
}

class ApplicationWorkflowService {
    private final SubsidyApplicationRepository repository;
    private final SmsNotificationObserver smsObserver = new SmsNotificationObserver();
    private final AuditTrailObserver auditObserver = new AuditTrailObserver();

    public ApplicationWorkflowService(SubsidyApplicationRepository repository) {
        this.repository = repository;
    }

    public SubsidyApplication submitApplication(Beneficiary beneficiary, Scheme scheme) 
            throws IneligibleBeneficiaryException, DuplicateApplicationException {
        
        boolean exists = repository.findAll().stream()
                .anyMatch(a -> a.getBeneficiary().getAadhaarNumber().equals(beneficiary.getAadhaarNumber()) 
                            && a.getScheme().getSchemeId().equals(scheme.getSchemeId())
                            && a.getStatus() != ApplicationStatus.REJECTED);
        if (exists) {
            throw new DuplicateApplicationException("Citizen already has an active application under: " + scheme.getSchemeName());
        }

        scheme.getEligibilityStrategy().evaluate(beneficiary, scheme);

        String appId = "TN-DBT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SubsidyApplication app = new SubsidyApplication(appId, beneficiary, scheme);
        app.attach(smsObserver);
        app.attach(auditObserver);

        repository.save(app);
        return app;
    }

    public void verifyApplication(String appId, String officerRemarks) {
        SubsidyApplication app = repository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
        app.verify(officerRemarks);
        repository.save(app);
    }

    public void allocateFunds(String appId) {
        SubsidyApplication app = repository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
        app.allocate(app.getScheme().getStandardGrant());
        repository.save(app);
    }

    public void disburseFunds(String appId) {
        SubsidyApplication app = repository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
        app.disburse();
        repository.save(app);
    }

    public void rejectApplication(String appId, String reason) {
        SubsidyApplication app = repository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
        app.reject(reason);
        repository.save(app);
    }
}

class ReportExportService {
    public static CompletableFuture<Path> exportToCsvAsync(Collection<SubsidyApplication> applications, String fileName) {
        return CompletableFuture.supplyAsync(() -> {
            Path path = Paths.get(fileName);
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                writer.write("Application_ID,Aadhaar,Name,District,Category,Scheme,Grant_Amount,Status,Remarks\n");
                for (SubsidyApplication app : applications) {
                    writer.write(String.format("%s,%s,\"%s\",%s,%s,\"%s\",%.2f,%s,\"%s\"\n",
                            app.getApplicationId(),
                            app.getBeneficiary().getAadhaarNumber(),
                            app.getBeneficiary().getFullName(),
                            app.getBeneficiary().getDistrict(),
                            app.getBeneficiary().getBeneficiaryType(),
                            app.getScheme().getSchemeName(),
                            app.getAllocatedAmount(),
                            app.getStatus(),
                            app.getRemarks() == null ? "" : app.getRemarks().replace("\"", "\"\"")));
                }
            } catch (IOException e) {
                throw new CompletionException(e);
            }
            return path;
        });
    }
}

/* =========================================================================
 * 11. PRESENTATION: SWING GRAPHICAL USER INTERFACE (GUI)
 * ========================================================================= */
class MainGuiWindow extends JFrame {
    private final SubsidyApplicationRepository repository;
    private final ApplicationWorkflowService workflowService;
    private final ParallelVerificationService verificationService;

    private final Map<String, Scheme> schemeRegistry = new LinkedHashMap<>();
    private final Map<String, Beneficiary> beneficiaryRegistry = new LinkedHashMap<>();

    private JTable applicationsTable;
    private DefaultTableModel tableModel;
    private JLabel statusBanner;

    private static final Color APP_BACKGROUND = new Color(245, 247, 250);
    private static final Color BRAND_NAVY = new Color(22, 45, 74);
    private static final Color BRAND_GREEN = new Color(24, 130, 84);
    private static final Color MUTED_TEXT = new Color(99, 112, 128);

    public MainGuiWindow() {
        this.repository = new SubsidyApplicationRepository();
        this.workflowService = new ApplicationWorkflowService(repository);
        this.verificationService = new ParallelVerificationService();

        seedTamilNaduSchemes();
        initializeUi();
    }

    private void seedTamilNaduSchemes() {
        Department agri = new Department("DEP-AGRI", "Agriculture & Farmers Welfare", "Thiru. C. Samayamoorthy IAS", "agri@tn.gov.in");
        Department social = new Department("DEP-SOC", "Social Welfare & Women Empowerment", "Tmt. Jayashree Muralidharan IAS", "socialwelfare@tn.gov.in");
        Department edu = new Department("DEP-EDU", "Higher Education Department", "Thiru. Pradeep Yadav IAS", "hedept@tn.gov.in");
        Department rural = new Department("DEP-RD", "Rural Development & Panchayat Raj", "Thiru. P. Ponniah IAS", "rdpr@tn.gov.in");

        Scheme dalp = SchemeFactory.createScheme(SchemeType.DESTITUTE_AGRICULTURAL_LABOURER_PENSION, "TN-DALP-01", "Destitute Agricultural Labourers Pension", agri, 50000000.0);
        Scheme oap = SchemeFactory.createScheme(SchemeType.OLD_AGE_PENSION, "TN-OAP-02", "Indira Gandhi Old Age Pension Scheme", social, 100000000.0);
        Scheme widow = SchemeFactory.createScheme(SchemeType.WIDOW_PENSION, "TN-WIDOW-03", "Destitute Widow Pension Scheme", social, 40000000.0);
        Scheme diffAbled = SchemeFactory.createScheme(SchemeType.DIFFERENTLY_ABLED_PENSION, "TN-DA-04", "Differently Abled Maintenance Allowance", social, 30000000.0);
        Scheme seeds = SchemeFactory.createScheme(SchemeType.FARMER_SUBSIDY_SEEDS, "TN-AGRI-SEED", "Quality Certified Paddy & Millet Seeds Subsidy", agri, 20000000.0);
        Scheme equip = SchemeFactory.createScheme(SchemeType.FARMER_SUBSIDY_EQUIPMENT, "TN-AGRI-MECH", "Agricultural Mechanization & Power Tiller Grant", agri, 80000000.0);
        Scheme pudhumai = SchemeFactory.createScheme(SchemeType.EDUCATION_PUDHUMAI_PENN, "TN-EDU-PP", "Moovalur Ramamirtham Pudhumai Penn Scheme", edu, 60000000.0);
        Scheme housing = SchemeFactory.createScheme(SchemeType.HOUSING_KALAIGNAR_KANAVU_ILLAM, "TN-HOUSE-KKI", "Kalaignar Kanavu Illam Rural Housing", rural, 250000000.0);

        for (Scheme s : List.of(dalp, oap, widow, diffAbled, seeds, equip, pudhumai, housing)) {
            schemeRegistry.put(s.getSchemeId(), s);
        }

        try {
            Beneficiary b1 = new FarmerBeneficiary("987654321012", "Murugan Chettiar", "Thanjavur", 72000.0, "9842112233", 3.2, "Paddy");
            beneficiaryRegistry.put(b1.getAadhaarNumber(), b1);
            workflowService.submitApplication(b1, seeds);
        } catch (Exception ignored) {}
    }

    private void initializeUi() {
        setTitle("Tamil Nadu | Citizen Benefit Portal");
        setSize(1280, 820);
        setMinimumSize(new Dimension(1050, 700));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(APP_BACKGROUND);

        JPanel bannerPanel = new JPanel(new BorderLayout());
        bannerPanel.setBackground(BRAND_NAVY);
        bannerPanel.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Tamil Nadu Citizen Benefit Portal");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 23));
        JLabel subtitle = new JLabel("Apply for support. Track your application. Receive benefits.");
        subtitle.setForeground(new Color(205, 219, 233));
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        brand.add(title);
        brand.add(subtitle);
        bannerPanel.add(brand, BorderLayout.WEST);

        statusBanner = new JLabel("●  Ready to help");
        statusBanner.setForeground(new Color(185, 235, 207));
        statusBanner.setFont(new Font("SansSerif", Font.BOLD, 13));
        bannerPanel.add(statusBanner, BorderLayout.EAST);
        add(bannerPanel, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 14));
        tabs.setBackground(APP_BACKGROUND);
        tabs.setBorder(BorderFactory.createEmptyBorder(10, 18, 12, 18));
        tabs.addTab("  New application  ", createCitizenTab());
        tabs.addTab("  Applications  ", createOfficerTab());
        tabs.addTab("  Available schemes  ", createDepartmentTab());
        tabs.addTab("  Payments & reports  ", createAdminTab());

        add(tabs, BorderLayout.CENTER);
    }

    private JPanel createCitizenTab() {
        JPanel panel = new JPanel(new BorderLayout(18, 18));
        panel.setBackground(APP_BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel intro = new JPanel();
        intro.setOpaque(false);
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Start a new application");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(BRAND_NAVY);
        JLabel description = new JLabel("Enter your details and choose the benefit that best fits your needs.");
        description.setFont(new Font("SansSerif", Font.PLAIN, 14));
        description.setForeground(MUTED_TEXT);
        description.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        intro.add(heading);
        intro.add(description);
        panel.add(intro, BorderLayout.NORTH);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 239)),
                BorderFactory.createEmptyBorder(20, 22, 20, 22)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 7, 8, 7);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        JTextField txtAadhaar = new JTextField(15);
        JTextField txtName = new JTextField(15);
        JComboBox<String> cmbDistrict = new JComboBox<>(new String[]{"Thanjavur", "Madurai", "Coimbatore", "Chennai", "Salem", "Tiruchirappalli", "Dindigul", "Tirunelveli"});
        JTextField txtIncome = new JTextField("60000");
        JTextField txtContact = new JTextField("9842100000");

        JComboBox<String> cmbCategory = new JComboBox<>(new String[]{
            "Farmer", "Senior citizen", "Widow", "Person with disability", "Student", "Agricultural worker"
        });

        JTextField txtExtraParam1 = new JTextField("3.0");
        JTextField txtExtraParam2 = new JTextField("Paddy");
        JLabel extraLabel1 = new JLabel("Land size (acres):");
        JLabel extraLabel2 = new JLabel("Crop:");

        JComboBox<String> cmbSchemes = new JComboBox<>();
        schemeRegistry.values().forEach(s -> cmbSchemes.addItem(s.getSchemeName()));

        int row = 0;
        addFormRow(card, gbc, row++, "Aadhaar number (12 digits):", txtAadhaar);
        addFormRow(card, gbc, row++, "Full name:", txtName);
        addFormRow(card, gbc, row++, "District:", cmbDistrict);
        addFormRow(card, gbc, row++, "Annual family income (INR):", txtIncome);
        addFormRow(card, gbc, row++, "Mobile number:", txtContact);
        addFormRow(card, gbc, row++, "Applicant category:", cmbCategory);
        addFormRow(card, gbc, row++, extraLabel1, txtExtraParam1);
        addFormRow(card, gbc, row++, extraLabel2, txtExtraParam2);
        addFormRow(card, gbc, row++, "Benefit scheme:", cmbSchemes);

        styleInput(txtAadhaar);
        styleInput(txtName);
        styleInput(cmbDistrict);
        styleInput(txtIncome);
        styleInput(txtContact);
        styleInput(cmbCategory);
        styleInput(txtExtraParam1);
        styleInput(txtExtraParam2);
        styleInput(cmbSchemes);

        cmbCategory.addActionListener(e -> {
            switch ((String) cmbCategory.getSelectedItem()) {
                case "Farmer" -> {
                    extraLabel1.setText("Land size (acres):");
                    extraLabel2.setText("Main crop:");
                    txtExtraParam1.setText("3.0");
                    txtExtraParam2.setText("Paddy");
                }
                case "Senior citizen" -> {
                    extraLabel1.setText("Age (years):");
                    extraLabel2.setText("Additional details:");
                    txtExtraParam1.setText("");
                    txtExtraParam2.setText("");
                }
                case "Widow" -> {
                    extraLabel1.setText("Certificate reference:");
                    extraLabel2.setText("Dependants:");
                    txtExtraParam1.setText("");
                    txtExtraParam2.setText("1");
                }
                case "Person with disability" -> {
                    extraLabel1.setText("UDID number:");
                    extraLabel2.setText("Disability percentage:");
                    txtExtraParam1.setText("");
                    txtExtraParam2.setText("");
                }
                case "Student" -> {
                    extraLabel1.setText("Studied in a government school (yes/no):");
                    extraLabel2.setText("Course:");
                    txtExtraParam1.setText("yes");
                    txtExtraParam2.setText("");
                }
                default -> {
                    extraLabel1.setText("Worker registration reference:");
                    extraLabel2.setText("Additional details:");
                    txtExtraParam1.setText("");
                    txtExtraParam2.setText("");
                }
            }
        });

        JButton btnApply = createActionButton("Submit application", BRAND_GREEN);
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.insets = new Insets(16, 7, 4, 7);
        card.add(btnApply, gbc);

        btnApply.addActionListener(e -> {
            try {
                String aadhaar = txtAadhaar.getText().trim();
                String name = txtName.getText().trim();
                String dist = (String) cmbDistrict.getSelectedItem();
                double inc = Double.parseDouble(txtIncome.getText().trim());
                String phone = txtContact.getText().trim();
                String cat = (String) cmbCategory.getSelectedItem();

                Beneficiary b;
                switch (cat) {
                    case "Farmer" -> b = new FarmerBeneficiary(aadhaar, name, dist, inc, phone,
                            Double.parseDouble(txtExtraParam1.getText().trim()), txtExtraParam2.getText().trim());
                    case "Senior citizen" -> b = new SeniorCitizenBeneficiary(aadhaar, name, dist, inc, phone,
                            Integer.parseInt(txtExtraParam1.getText().trim()));
                    case "Widow" -> b = new WidowBeneficiary(aadhaar, name, dist, inc, phone,
                            txtExtraParam1.getText().trim(), Integer.parseInt(txtExtraParam2.getText().trim()));
                    case "Person with disability" -> b = new DifferentlyAbledBeneficiary(aadhaar, name, dist, inc, phone,
                            txtExtraParam1.getText().trim(), Integer.parseInt(txtExtraParam2.getText().trim()));
                    case "Student" -> b = new StudentBeneficiary(aadhaar, name, dist, inc, phone,
                            "yes".equalsIgnoreCase(txtExtraParam1.getText().trim()), "Tamil Nadu", txtExtraParam2.getText().trim());
                    default -> b = new DestituteLabourerBeneficiary(aadhaar, name, dist, inc, phone, txtExtraParam1.getText().trim());
                }

                beneficiaryRegistry.put(b.getAadhaarNumber(), b);

                Scheme scheme = schemeRegistry.values().stream()
                        .filter(s -> s.getSchemeName().equals(cmbSchemes.getSelectedItem()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Please choose a benefit scheme."));

                SubsidyApplication application = workflowService.submitApplication(b, scheme);
                JOptionPane.showMessageDialog(this,
                        "Your application has been received.\n\nTracking number: " + application.getApplicationId() +
                        "\nBenefit amount: INR " + String.format("%,.2f", scheme.getStandardGrant()),
                        "Application submitted", JOptionPane.INFORMATION_MESSAGE);

                refreshApplicationTable();

            } catch (InvalidAadhaarException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Check your details", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Unable to submit application", JOptionPane.WARNING_MESSAGE);
            }
        });

        JPanel formArea = new JPanel(new BorderLayout());
        formArea.setOpaque(false);
        formArea.add(card, BorderLayout.NORTH);
        JPanel helpCard = new JPanel();
        helpCard.setBackground(new Color(232, 242, 250));
        helpCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(216, 229, 240)),
                BorderFactory.createEmptyBorder(18, 20, 18, 20)));
        helpCard.setLayout(new BoxLayout(helpCard, BoxLayout.Y_AXIS));
        JLabel helpTitle = new JLabel("Before you begin");
        helpTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        helpTitle.setForeground(BRAND_NAVY);
        JLabel helpText = new JLabel("<html>Keep your Aadhaar and mobile number handy.<br><br>"
                + "Choose the applicant category that applies to you and enter the requested details.<br><br>"
                + "After submitting, save your tracking number to check progress.</html>");
        helpText.setFont(new Font("SansSerif", Font.PLAIN, 13));
        helpText.setForeground(MUTED_TEXT);
        helpText.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        helpCard.add(helpTitle);
        helpCard.add(helpText);

        JPanel content = new JPanel(new BorderLayout(18, 0));
        content.setOpaque(false);
        content.add(formArea, BorderLayout.CENTER);
        content.add(helpCard, BorderLayout.EAST);
        helpCard.setPreferredSize(new Dimension(245, 180));
        JScrollPane formScroll = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setBorder(null);
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formScroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(formScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createOfficerTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(APP_BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel intro = new JPanel();
        intro.setOpaque(false);
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Application progress");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(BRAND_NAVY);
        JLabel description = new JLabel("Review applications, confirm details, and update their progress.");
        description.setFont(new Font("SansSerif", Font.PLAIN, 14));
        description.setForeground(MUTED_TEXT);
        description.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        intro.add(heading);
        intro.add(description);
        panel.add(intro, BorderLayout.NORTH);

        String[] cols = {"Tracking number", "Aadhaar", "Applicant", "District", "Benefit scheme", "Progress", "Benefit (INR)"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        applicationsTable = new JTable(tableModel);
        styleTable(applicationsTable);
        refreshApplicationTable();

        JScrollPane tableScroll = new JScrollPane(applicationsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 239)));
        panel.add(tableScroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new BorderLayout(0, 8));
        btnPanel.setOpaque(true);
        btnPanel.setBackground(Color.WHITE);
        btnPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(205, 214, 224)),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel actionHint = new JLabel("Select an application row above, then choose an action:");
        actionHint.setFont(new Font("SansSerif", Font.BOLD, 13));
        actionHint.setForeground(BRAND_NAVY);
        btnPanel.add(actionHint, BorderLayout.NORTH);
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionButtons.setOpaque(false);
        JButton btnVerifyParallel = createActionButton("Check details", new Color(49, 91, 150));
        JButton btnApprove = createActionButton("Mark as verified", BRAND_GREEN);
        JButton btnReject = createActionButton("Decline application", new Color(170, 63, 63));

        actionButtons.add(btnVerifyParallel);
        actionButtons.add(btnApprove);
        actionButtons.add(btnReject);
        btnPanel.add(actionButtons, BorderLayout.SOUTH);
        panel.add(btnPanel, BorderLayout.SOUTH);

        btnVerifyParallel.addActionListener(e -> {
            int row = applicationsTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Select an application to verify.");
                return;
            }
            String appId = (String) tableModel.getValueAt(row, 0);
            SubsidyApplication app = repository.findById(appId).orElseThrow();

            statusBanner.setText("Checking application details...");

            new SwingWorker<ParallelVerificationService.VerificationOutcome, Void>() {
                @Override
                protected ParallelVerificationService.VerificationOutcome doInBackground() {
                    return verificationService.performParallelVerificationAsync(app.getBeneficiary()).join();
                }

                @Override
                protected void done() {
                    try {
                        ParallelVerificationService.VerificationOutcome outcome = get();
                        statusBanner.setText("●  Ready to help");
                        JOptionPane.showMessageDialog(MainGuiWindow.this,
                                String.format("""
                                        Application checks complete

                                        Identity details: %s
                                        District details: %s
                                        Bank account details: %s

                                        Overall result: %s""",
                                        outcome.aadhaarValid() ? "Complete" : "Needs review",
                                        outcome.pattaOrRegistryValid() ? "Complete" : "Needs review",
                                        outcome.npciBankLinked() ? "Complete" : "Needs review",
                                        outcome.isAllPassed() ? "All checks passed" : "Please review the details"),
                                "Application check", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        statusBanner.setText("●  Ready to help");
                        JOptionPane.showMessageDialog(MainGuiWindow.this, ex.getMessage(),
                                "Unable to check application", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        btnApprove.addActionListener(e -> {
            int row = applicationsTable.getSelectedRow();
            if (row == -1) return;
            String appId = (String) tableModel.getValueAt(row, 0);
            try {
                workflowService.verifyApplication(appId, "Field inspection completed by Tahsildar / VAO.");
                refreshApplicationTable();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Workflow Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnReject.addActionListener(e -> {
            int row = applicationsTable.getSelectedRow();
            if (row == -1) return;
            String appId = (String) tableModel.getValueAt(row, 0);
            String reason = JOptionPane.showInputDialog(this, "Enter rejection remarks:");
            if (reason != null && !reason.isBlank()) {
                workflowService.rejectApplication(appId, reason);
                refreshApplicationTable();
            }
        });

        return panel;
    }

    private JPanel createDepartmentTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(APP_BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel intro = new JPanel();
        intro.setOpaque(false);
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Benefits and schemes");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(BRAND_NAVY);
        JLabel description = new JLabel("Explore the support currently available across Tamil Nadu.");
        description.setFont(new Font("SansSerif", Font.PLAIN, 14));
        description.setForeground(MUTED_TEXT);
        description.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        intro.add(heading);
        intro.add(description);
        panel.add(intro, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(0, 2, 14, 14));
        cards.setBackground(APP_BACKGROUND);
        for (Scheme s : schemeRegistry.values()) {
            JPanel card = new JPanel();
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 239)),
                    BorderFactory.createEmptyBorder(16, 18, 16, 18)));
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            JLabel name = new JLabel("<html><b>" + s.getSchemeName() + "</b></html>");
            name.setFont(new Font("SansSerif", Font.BOLD, 15));
            name.setForeground(BRAND_NAVY);
            JLabel department = new JLabel(s.getDepartment().getDepartmentName());
            department.setFont(new Font("SansSerif", Font.PLAIN, 12));
            department.setForeground(MUTED_TEXT);
            JLabel grant = new JLabel(String.format("Benefit amount  |  INR %,.2f", s.getStandardGrant()));
            grant.setFont(new Font("SansSerif", Font.BOLD, 13));
            grant.setForeground(BRAND_GREEN);
            JLabel budget = new JLabel(String.format("Scheme budget  |  INR %,.0f", s.getTotalBudget()));
            budget.setFont(new Font("SansSerif", Font.PLAIN, 12));
            budget.setForeground(MUTED_TEXT);
            name.setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));
            department.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
            grant.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
            card.add(name);
            card.add(department);
            card.add(grant);
            card.add(budget);
            cards.add(card);
        }

        JPanel grid = new JPanel(new BorderLayout());
        grid.setBackground(APP_BACKGROUND);
        grid.add(cards, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(APP_BACKGROUND);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAdminTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setBackground(APP_BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel intro = new JPanel();
        intro.setOpaque(false);
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Payments and reports");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(BRAND_NAVY);
        JLabel description = new JLabel("Manage approved benefits and download an application report.");
        description.setFont(new Font("SansSerif", Font.PLAIN, 14));
        description.setForeground(MUTED_TEXT);
        description.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        intro.add(heading);
        intro.add(description);
        panel.add(intro, BorderLayout.NORTH);

        JPanel actions = new JPanel(new GridLayout(1, 3, 14, 14));
        actions.setOpaque(false);
        JButton btnSanction = createActionButton("Approve benefit amount", new Color(49, 91, 150));
        JButton btnDisburse = createActionButton("Mark payment as sent", BRAND_GREEN);
        JButton btnExport = createActionButton("Download application report", new Color(78, 91, 108));
        actions.add(btnSanction);
        actions.add(btnDisburse);
        actions.add(btnExport);
        actions.setPreferredSize(new Dimension(0, 62));
        JPanel actionArea = new JPanel(new BorderLayout(0, 10));
        actionArea.setOpaque(true);
        actionArea.setBackground(Color.WHITE);
        actionArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(205, 214, 224)),
            BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        JLabel paymentHint = new JLabel("For payment actions, select an application row in the Applications tab first. Reports include all applications.");
        paymentHint.setFont(new Font("SansSerif", Font.BOLD, 13));
        paymentHint.setForeground(BRAND_NAVY);
        actionArea.add(paymentHint, BorderLayout.NORTH);
        actionArea.add(actions, BorderLayout.CENTER);
        panel.add(actionArea, BorderLayout.CENTER);

        btnSanction.addActionListener(e -> {
            int row = applicationsTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Select an application in the Applications tab first.");
                return;
            }
            String appId = (String) tableModel.getValueAt(row, 0);
            try {
                workflowService.allocateFunds(appId);
                refreshApplicationTable();
                JOptionPane.showMessageDialog(this, "The benefit amount has been approved for application " + appId + ".");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Workflow Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnDisburse.addActionListener(e -> {
            int row = applicationsTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Select an application in the Applications tab first.");
                return;
            }
            String appId = (String) tableModel.getValueAt(row, 0);
            try {
                workflowService.disburseFunds(appId);
                refreshApplicationTable();
                JOptionPane.showMessageDialog(this, "The payment has been marked as sent.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Workflow Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnExport.addActionListener(e -> {
            statusBanner.setText("Preparing your report...");
            ReportExportService.exportToCsvAsync(repository.findAll(), "TN_DBT_Disbursement_Audit.csv")
                    .thenAccept(path -> SwingUtilities.invokeLater(() -> {
                        statusBanner.setText("●  Ready to help");
                        JOptionPane.showMessageDialog(this, "Your report is ready:\n" + path.toAbsolutePath());
                    }))
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> {
                            statusBanner.setText("●  Ready to help");
                            JOptionPane.showMessageDialog(this, "Unable to create report: " + ex.getMessage(),
                                    "Report error", JOptionPane.ERROR_MESSAGE);
                        });
                        return null;
                    });
        });

        return panel;
    }

    private void refreshApplicationTable() {
        tableModel.setRowCount(0);
        for (SubsidyApplication app : repository.findAll()) {
            tableModel.addRow(new Object[]{
                    app.getApplicationId(),
                    app.getBeneficiary().getAadhaarNumber(),
                    app.getBeneficiary().getFullName(),
                    app.getBeneficiary().getDistrict(),
                    app.getScheme().getSchemeName(),
                    app.getStatus().name().replace('_', ' '),
                    app.getAllocatedAmount()
            });
        }
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        addFormRow(panel, gbc, row, new JLabel(label), field);
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, JLabel label, JComponent field) {
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(new Color(54, 68, 84));
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.38;
        panel.add(label, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.62;
        panel.add(field, gbc);
    }

    private void styleInput(JComponent input) {
        input.setFont(new Font("SansSerif", Font.PLAIN, 14));
        input.setPreferredSize(new Dimension(300, 38));
        if (input instanceof JTextField) {
            input.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(205, 214, 224)),
                    BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        } else if (input instanceof JComboBox<?> comboBox) {
            input.setBackground(Color.WHITE);
            input.setForeground(new Color(35, 48, 63));
            comboBox.setFocusable(true);
            comboBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean isSelected, boolean cellHasFocus) {
                    JLabel item = (JLabel) super.getListCellRendererComponent(
                            list, value, index, isSelected, cellHasFocus);
                    item.setOpaque(true);
                    item.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                    item.setBackground(isSelected ? new Color(49, 91, 150) : Color.WHITE);
                    item.setForeground(isSelected ? Color.WHITE : new Color(35, 48, 63));
                    return item;
                }
            });
        }
    }

    private JButton createActionButton(String text, Color background) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(background);
        button.setOpaque(true);
        button.setBorderPainted(true);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(background.darker()),
            BorderFactory.createEmptyBorder(11, 16, 11, 16)));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void styleTable(JTable table) {
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(235, 239, 244));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.getTableHeader().setBackground(new Color(237, 242, 247));
        table.getTableHeader().setForeground(BRAND_NAVY);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
    }
}

/* =========================================================================
 * 12. PRESENTATION: INTERACTIVE CONSOLE (CLI)
 * ========================================================================= */
class ConsoleApp {
    private final SubsidyApplicationRepository repo = new SubsidyApplicationRepository();
    private final ApplicationWorkflowService workflowService = new ApplicationWorkflowService(repo);
    private final ParallelVerificationService verificationService = new ParallelVerificationService();
    private final Map<String, Scheme> schemes = new LinkedHashMap<>();
    private final Scanner scanner = new Scanner(System.in);

    public void start() {
        seedInitialData();
        while (true) {
            System.out.println("\n=======================================================");
            System.out.println("  GOVERNMENT OF TAMIL NADU — SUBSIDY BENEFICIARY TRACKER");
            System.out.println("=======================================================");
            System.out.println("1. Citizen Portal: Register & Apply for TN Scheme");
            System.out.println("2. Officer Desk : Run Parallel Verification & Approve");
            System.out.println("3. Department   : View Schemes, Grants & Budgets");
            System.out.println("4. Super Admin  : Sanction, Disburse DBT & Export CSV");
            System.out.println("5. Exit");
            System.out.print("Select Menu Option [1-5]: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> handleCitizenApply();
                case "2" -> handleOfficerDesk();
                case "3" -> handleDepartmentView();
                case "4" -> handleAdminDisburse();
                case "5" -> {
                    verificationService.shutdown();
                    System.out.println("System shutting down. Vanakkam!");
                    return;
                }
                default -> System.out.println("Invalid selection. Try again.");
            }
        }
    }

    private void seedInitialData() {
        Department social = new Department("DEP-SOC", "Social Welfare", "Tmt. Jayashree Muralidharan IAS", "social@tn.gov.in");
        Department agri = new Department("DEP-AGRI", "Agriculture", "Thiru. C. Samayamoorthy IAS", "agri@tn.gov.in");

        Scheme oap = SchemeFactory.createScheme(SchemeType.OLD_AGE_PENSION, "TN-OAP", "Indira Gandhi Old Age Pension", social, 5000000.0);
        Scheme seed = SchemeFactory.createScheme(SchemeType.FARMER_SUBSIDY_SEEDS, "TN-SEED", "Paddy Seeds Input Subsidy", agri, 2000000.0);

        schemes.put(oap.getSchemeId(), oap);
        schemes.put(seed.getSchemeId(), seed);
    }

    private void handleCitizenApply() {
        try {
            System.out.print("Enter 12-digit Aadhaar Number: ");
            String aadhaar = scanner.nextLine().trim();
            System.out.print("Enter Beneficiary Full Name: ");
            String name = scanner.nextLine().trim();
            System.out.print("Enter District in Tamil Nadu: ");
            String dist = scanner.nextLine().trim();
            System.out.print("Enter Annual Income (₹): ");
            double inc = Double.parseDouble(scanner.nextLine().trim());
            System.out.print("Enter Contact Phone Number: ");
            String phone = scanner.nextLine().trim();

            System.out.println("Select Category: 1. Farmer  2. Senior Citizen");
            String cat = scanner.nextLine().trim();
            Beneficiary b;
            if ("1".equals(cat)) {
                System.out.print("Enter Land Size (Acres): ");
                double acres = Double.parseDouble(scanner.nextLine().trim());
                b = new FarmerBeneficiary(aadhaar, name, dist, inc, phone, acres, "Paddy");
            } else {
                System.out.print("Enter Age: ");
                int age = Integer.parseInt(scanner.nextLine().trim());
                b = new SeniorCitizenBeneficiary(aadhaar, name, dist, inc, phone, age);
            }

            System.out.println("Available Schemes: " + schemes.keySet());
            System.out.print("Enter Scheme ID: ");
            String sid = scanner.nextLine().trim();
            Scheme scheme = schemes.get(sid);

            if (scheme == null) {
                System.out.println("Scheme not found.");
                return;
            }

            SubsidyApplication app = workflowService.submitApplication(b, scheme);
            System.out.println("[SUCCESS] Application registered with ID: " + app.getApplicationId());

        } catch (Exception e) {
            System.err.println("[ERROR] Failed: " + e.getMessage());
        }
    }

    private void handleOfficerDesk() {
        Collection<SubsidyApplication> list = repo.findAll();
        if (list.isEmpty()) {
            System.out.println("No applications currently queued.");
            return;
        }
        list.forEach(a -> System.out.printf("App ID: %s | Aadhaar: %s | Citizen: %s | State: %s%n",
                a.getApplicationId(), a.getBeneficiary().getAadhaarNumber(), a.getBeneficiary().getFullName(), a.getStatus()));

        System.out.print("Enter Application ID to verify: ");
        String aid = scanner.nextLine().trim();
        repo.findById(aid).ifPresentOrElse(app -> {
            System.out.println("Running multithreaded background verification across UIDAI, Tamil Nilam & NPCI...");
            ParallelVerificationService.VerificationOutcome outcome = 
                    verificationService.performParallelVerificationAsync(app.getBeneficiary()).join();

            System.out.println("Verification Checks Complete: " + outcome);
            if (outcome.isAllPassed()) {
                workflowService.verifyApplication(aid, "CLI verification approved by Field Officer.");
                System.out.println("Application state updated: VERIFIED");
            } else {
                workflowService.rejectApplication(aid, "Failed parallel registry checks.");
                System.out.println("Application state updated: REJECTED");
            }
        }, () -> System.out.println("Application ID not found."));
    }

    private void handleDepartmentView() {
        System.out.println("\n--- TAMIL NADU WELFARE SCHEME BUDGET AUDIT ---");
        for (Scheme s : schemes.values()) {
            System.out.printf("Scheme: %-30s | Grant: ₹%-8.2f | Total Budget: ₹%,-12.0f | Allocated: ₹%,-12.0f%n",
                    s.getSchemeName(), s.getStandardGrant(), s.getTotalBudget(), s.getAllocatedBudget());
        }
    }

    private void handleAdminDisburse() {
        System.out.print("Enter Application ID to Allocate & Disburse DBT: ");
        String aid = scanner.nextLine().trim();
        repo.findById(aid).ifPresentOrElse(app -> {
            workflowService.allocateFunds(aid);
            workflowService.disburseFunds(aid);
            System.out.printf("[SUCCESS] Funds of ₹%.2f disbursed via DBT to %s%n",
                    app.getAllocatedAmount(), app.getBeneficiary().getFullName());
        }, () -> System.out.println("Application ID not found."));

        System.out.print("Export all audit records to CSV? (y/n): ");
        if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
            ReportExportService.exportToCsvAsync(repo.findAll(), "TN_DBT_Audit_CLI.csv")
                    .thenAccept(p -> System.out.println("CSV Export ready at: " + p.toAbsolutePath()))
                    .join();
        }
    }
}