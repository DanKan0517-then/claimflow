package com.claimflow.seed;

import com.claimflow.ai.AnalysisJobQueue;
import com.claimflow.audit.AuditLog;
import com.claimflow.audit.AuditLogRepository;
import com.claimflow.claim.Claim;
import com.claimflow.claim.ClaimRepository;
import com.claimflow.claim.ClaimStatus;
import com.claimflow.config.AppProperties;
import com.claimflow.policy.Policy;
import com.claimflow.policy.PolicyRepository;
import com.claimflow.policy.PolicyType;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads demo accounts and a realistic spread of claims so the dashboards aren't empty.
 * Runs only when SEED_DEMO=true and the database has no users. Open claims are pushed through
 * the real AI pipeline; closed ones get their outcome written directly.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Demo@1234";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final PolicyRepository policies;
    private final ClaimRepository claims;
    private final AuditLogRepository audit;
    private final PasswordEncoder encoder;
    private final AnalysisJobQueue queue;
    private final TransactionTemplate tx;

    public DemoDataSeeder(AppProperties props, UserRepository users, PolicyRepository policies, ClaimRepository claims,
                          AuditLogRepository audit, PasswordEncoder encoder, AnalysisJobQueue queue,
                          PlatformTransactionManager txManager) {
        this.props = props;
        this.users = users;
        this.policies = policies;
        this.claims = claims;
        this.audit = audit;
        this.encoder = encoder;
        this.queue = queue;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seedDemo() || users.count() > 0) return;
        List<Long> toAnalyze = tx.execute(status -> seed());
        if (toAnalyze != null) toAnalyze.forEach(queue::enqueue);
        log.info("Demo data loaded. Log in with any demo account, password {}", DEMO_PASSWORD);
    }

    private List<Long> seed() {
        String pw = encoder.encode(DEMO_PASSWORD);
        users.save(new User("admin@claimflow.dev", pw, "Arjun Mehta", Role.ADMIN));
        User manager = users.save(new User("manager@claimflow.dev", pw, "Kavya Iyer", Role.MANAGER));
        User adj1 = users.save(new User("adjuster@claimflow.dev", pw, "Rahul Verma", Role.ADJUSTER));
        User adj2 = users.save(new User("adjuster2@claimflow.dev", pw, "Sneha Reddy", Role.ADJUSTER));
        User priya = users.save(new User("customer@claimflow.dev", pw, "Priya Sharma", Role.CUSTOMER));
        User karthik = users.save(new User("karthik@claimflow.dev", pw, "Karthik Subramanian", Role.CUSTOMER));
        User fatima = users.save(new User("fatima@claimflow.dev", pw, "Fatima Khan", Role.CUSTOMER));

        LocalDate today = LocalDate.now();
        Policy pHealth = policy(priya, PolicyType.HEALTH, "500000", "12500", today.minusMonths(8));
        Policy pMotor = policy(priya, PolicyType.MOTOR, "300000", "8900", today.minusMonths(5));
        Policy kHealth = policy(karthik, PolicyType.HEALTH, "1000000", "21000", today.minusMonths(11));
        Policy kProperty = policy(karthik, PolicyType.PROPERTY, "2500000", "15000", today.minusDays(20));
        Policy fTravel = policy(fatima, PolicyType.TRAVEL, "200000", "3200", today.minusMonths(2));
        Policy fMotor = policy(fatima, PolicyType.MOTOR, "450000", "11200", today.minusMonths(10));

        List<Long> open = new ArrayList<>();
        // open claims: go through the real AI pipeline
        open.add(openClaim(priya, pHealth, "Appendectomy at Apollo Hospital",
                "Admitted with severe abdominal pain, diagnosed with appendicitis and operated the same night. Stayed 3 days. Hospital bill attached.",
                today.minusDays(6), "84500", 1));
        open.add(openClaim(karthik, kProperty, "Kitchen fire damage",
                "Gas cylinder leak caused a fire in the kitchen. Total loss of cabinets and appliances. I paid the carpenter in cash and have no receipt yet.",
                today.minusDays(4), "2300000", 0));
        open.add(openClaim(fatima, fTravel, "Lost baggage on return flight",
                "Checked-in suitcase did not arrive at Chennai airport after my flight from Dubai. Airline confirmed it is lost after 21 days.",
                today.minusDays(25), "38000", 2));
        open.add(openClaim(priya, pMotor, "Rear bumper damage in parking lot",
                "Another car reversed into my car at the mall parking. Rear bumper and tail light need replacement. Garage estimate attached.",
                today.minusDays(2), "27500", 0));
        open.add(openClaim(fatima, fMotor, "Windshield cracked by falling branch",
                "During heavy rain a tree branch fell on the car and cracked the windshield. Photos taken at the spot.",
                today.minusDays(1), "18000", 0));

        // closed claims with outcomes (gives the dashboard history)
        closed(priya, pHealth, "Dengue treatment", "Hospitalised for 4 days with dengue fever, platelet transfusion done. Bills attached.",
                12, "62000", "62000", ClaimStatus.PAID, 18, adj1, manager);
        closed(karthik, kHealth, "Knee arthroscopy", "Ligament tear while playing football, arthroscopic surgery at Fortis.",
                10, "145000", "140000", ClaimStatus.APPROVED, 30, adj2, manager);
        closed(fatima, fMotor, "Scratch repair", "Minor scratches on the left door, want full repaint of the car.",
                9, "40000", null, ClaimStatus.REJECTED, 22, adj1, manager);
        closed(priya, pMotor, "Side mirror replacement", "Side mirror broken by a two-wheeler in traffic.",
                7, "6500", "6500", ClaimStatus.PAID, 5, adj2, manager);
        closed(karthik, kHealth, "Dental surgery", "Wisdom tooth extraction under general anaesthesia.",
                5, "32000", "28000", ClaimStatus.APPROVED, 60, adj1, manager);
        closed(fatima, fTravel, "Flight delay hotel stay", "Flight delayed by 14 hours in Doha, paid for hotel and meals.",
                3, "12000", "12000", ClaimStatus.PAID, 10, adj2, manager);
        return open;
    }

    private Policy policy(User holder, PolicyType type, String sum, String premium, LocalDate start) {
        String number = "POL-" + Year.now().getValue() + "-" + policies.nextPolicyNumber();
        return policies.save(new Policy(number, holder.getId(), type, new BigDecimal(sum), new BigDecimal(premium),
                start, start.plusYears(1)));
    }

    private Long openClaim(User customer, Policy policy, String title, String description, LocalDate incident,
                           String amount, int daysAgo) {
        Claim c = newClaim(customer, policy, title, description, incident, amount, daysAgo);
        c.transitionTo(ClaimStatus.SUBMITTED);
        c.transitionTo(ClaimStatus.AI_REVIEW);
        c = claims.save(c);
        audit.save(new AuditLog(c.getId(), customer.getId(), "SUBMITTED", "DRAFT", "SUBMITTED", null));
        return c.getId();
    }

    private void closed(User customer, Policy policy, String title, String description, int daysAgo, String amount,
                        String approved, ClaimStatus outcome, int fraud, User adjuster, User manager) {
        Claim c = newClaim(customer, policy, title, description,
                LocalDate.now().minusDays(daysAgo + 2L), amount, daysAgo);
        c.transitionTo(ClaimStatus.SUBMITTED);
        c.transitionTo(ClaimStatus.AI_REVIEW);
        c.transitionTo(ClaimStatus.UNDER_REVIEW);
        c.setAssignedAdjusterId(adjuster.getId());
        c.setFraudScore(fraud);
        c.setFraudFlags(fraud >= 20 ? "Claim is over 60% of the sum insured" : "");
        c.setAiProvider("rules");
        c.setAiSummary(title + " - claim of ₹" + amount + ".");
        if (outcome == ClaimStatus.REJECTED) {
            c.transitionTo(ClaimStatus.REJECTED);
            c.setDecisionReason("Cosmetic repaint of undamaged panels is not covered under the policy.");
        } else {
            c.setApprovedAmount(new BigDecimal(approved));
            c.transitionTo(ClaimStatus.APPROVED);
            if (outcome == ClaimStatus.PAID) c.transitionTo(ClaimStatus.PAID);
        }
        Instant decided = c.getCreatedAt().plus(10 + daysAgo * 3L, ChronoUnit.HOURS);
        c.setDecidedAt(decided);
        c = claims.save(c);
        audit.save(new AuditLog(c.getId(), customer.getId(), "SUBMITTED", "DRAFT", "SUBMITTED", null));
        audit.save(new AuditLog(c.getId(), adjuster.getId(), outcome == ClaimStatus.REJECTED ? "REJECTED" : "APPROVED",
                "UNDER_REVIEW", outcome == ClaimStatus.REJECTED ? "REJECTED" : "APPROVED", null));
        if (outcome == ClaimStatus.PAID) {
            audit.save(new AuditLog(c.getId(), manager.getId(), "PAID", "APPROVED", "PAID", null));
        }
    }

    private Claim newClaim(User customer, Policy policy, String title, String description, LocalDate incident,
                           String amount, int daysAgo) {
        String number = "CLM-" + Year.now().getValue() + "-" + claims.nextClaimNumber();
        Claim c = new Claim(number, policy.getId(), customer.getId(), policy.getType(), title, description, incident,
                new BigDecimal(amount));
        Instant created = Instant.now().minus(daysAgo, ChronoUnit.DAYS).minus(3, ChronoUnit.HOURS);
        c.setCreatedAt(created);
        c.setSlaDueAt(created.plus(policy.getType().sla()));
        return c;
    }
}
