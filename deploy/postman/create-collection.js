const fs = require('fs');

const baseUrl = 'http://localhost:8080';

const requests = [
  {
    name: '1. Recruiter Login',
    description: 'Recruiter login - gets recruiter JWT token',
    request: {
      method: 'POST',
      header: [{ key: 'Content-Type', value: 'application/json' }],
      body: { mode: 'raw', raw: '{\n  "email": "recruiter@smarthire.local",\n  "password": "Recruiter123!"\n}' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'auth', 'login'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.environment.set("recruiterToken", jsonData.accessToken);',
      'console.log("Recruiter token stored");'
    ]
  },
  {
    name: '2. Candidate Registration',
    description: 'Register a new candidate',
    request: {
      method: 'POST',
      header: [{ key: 'Content-Type', value: 'application/json' }],
      body: { mode: 'raw', raw: '{\n  "email": "candidate@demo.com",\n  "password": "Candidate123!Long"\n}' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'auth', 'register'] }
    },
    testScript: [
      'pm.test("Status 200 or 409", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'if (jsonData.userId) {',
      '  pm.environment.set("candidateId", jsonData.userId);',
      '  console.log("Candidate ID: " + jsonData.userId);',
      '} else {',
      '  console.log("User already exists, using existing candidate");',
      '}'
    ]
  },
  {
    name: '3. Candidate Login',
    description: 'Candidate login - gets candidate JWT token',
    request: {
      method: 'POST',
      header: [{ key: 'Content-Type', value: 'application/json' }],
      body: { mode: 'raw', raw: '{\n  "email": "candidate@demo.com",\n  "password": "Candidate123!Long"\n}' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'auth', 'login'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.environment.set("candidateToken", jsonData.accessToken);',
      'console.log("Candidate token stored");'
    ]
  },
  {
    name: '4. Recruiter Create Job',
    description: 'Recruiter creates a new job (DRAFT)',
    request: {
      method: 'POST',
      header: [
        { key: 'Content-Type', value: 'application/json' },
        { key: 'Authorization', value: 'Bearer {{recruiterToken}}' }
      ],
      body: { mode: 'raw', raw: '{\n  "companyName": "SmartHire Demo Company",\n  "role": "Software Engineer",\n  "location": "Remote"\n}' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'jobs'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.environment.set("jobId", jsonData.jobId);',
      'console.log("Job ID: " + jsonData.jobId);',
      'console.log("Job Status: " + jsonData.status);'
    ]
  },
  {
    name: '5. Recruiter Open Job',
    description: 'Recruiter opens the job (DRAFT -> OPEN)',
    request: {
      method: 'POST',
      header: [
        { key: 'Content-Type', value: 'application/json' },
        { key: 'Authorization', value: 'Bearer {{recruiterToken}}' }
      ],
      body: { mode: 'raw', raw: '' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'jobs', '{{jobId}}', 'open'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.test("Job is OPEN", function () { pm.expect(jsonData.status).to.eql("OPEN"); });',
      'console.log("Job opened: " + jsonData.jobId);'
    ]
  },
  {
    name: '6. Candidate View Job',
    description: 'Candidate views the OPEN job',
    request: {
      method: 'GET',
      header: [{ key: 'Authorization', value: 'Bearer {{candidateToken}}' }],
      body: { mode: 'raw', raw: '' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'jobs', '{{jobId}}'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.test("Job is OPEN", function () { pm.expect(jsonData.status).to.eql("OPEN"); });',
      'console.log("Viewed job: " + jsonData.role);'
    ]
  },
  {
    name: '7. Candidate Apply',
    description: 'Candidate applies to the OPEN job',
    request: {
      method: 'POST',
      header: [
        { key: 'Content-Type', value: 'application/json' },
        { key: 'Authorization', value: 'Bearer {{candidateToken}}' }
      ],
      body: { mode: 'raw', raw: '{\n  "jobId": "{{jobId}}",\n  "resumeLink": "https://example.com/resume.pdf"\n}' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'applications'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.environment.set("applicationId", jsonData.applicationId);',
      'console.log("Application ID: " + jsonData.applicationId);',
      'console.log("Application Status: " + jsonData.status);'
    ]
  },
  {
    name: '8. Recruiter Create Recruitment',
    description: 'Recruiter shortlists candidate (creates recruitment)',
    request: {
      method: 'POST',
      header: [
        { key: 'Content-Type', value: 'application/json' },
        { key: 'Authorization', value: 'Bearer {{recruiterToken}}' }
      ],
      body: { mode: 'raw', raw: '{\n  "applicationId": "{{applicationId}}"\n}' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'recruitments'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.environment.set("recruitmentId", jsonData.recruitmentId);',
      'console.log("Recruitment ID: " + jsonData.recruitmentId);',
      'console.log("Interview Status: " + jsonData.interviewStatus);'
    ]
  },
  {
    name: '9. Recruiter Schedule Interview',
    description: 'Recruiter schedules interview',
    request: {
      method: 'PATCH',
      header: [
        { key: 'Content-Type', value: 'application/json' },
        { key: 'Authorization', value: 'Bearer {{recruiterToken}}' }
      ],
      body: { mode: 'raw', raw: '{\n  "status": "SCHEDULED"\n}' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'recruitments', '{{recruitmentId}}', 'interview-status'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.test("Interview SCHEDULED", function () { pm.expect(jsonData.interviewStatus).to.eql("SCHEDULED"); });',
      'console.log("Interview scheduled");'
    ]
  },
  {
    name: '10. Recruiter Complete Interview',
    description: 'Recruiter completes interview',
    request: {
      method: 'PATCH',
      header: [
        { key: 'Content-Type', value: 'application/json' },
        { key: 'Authorization', value: 'Bearer {{recruiterToken}}' }
      ],
      body: { mode: 'raw', raw: '{\n  "status": "COMPLETED"\n}' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'recruitments', '{{recruitmentId}}', 'interview-status'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.test("Interview COMPLETED", function () { pm.expect(jsonData.interviewStatus).to.eql("COMPLETED"); });',
      'console.log("Interview completed");'
    ]
  },
  {
    name: '11. Recruiter Set Final Status',
    description: 'Recruiter sets final status to HIRED',
    request: {
      method: 'PATCH',
      header: [
        { key: 'Content-Type', value: 'application/json' },
        { key: 'Authorization', value: 'Bearer {{recruiterToken}}' }
      ],
      body: { mode: 'raw', raw: '{\n  "status": "HIRED"\n}' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'recruitments', '{{recruitmentId}}', 'final-status'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'pm.test("Final Status HIRED", function () { pm.expect(jsonData.finalStatus).to.eql("HIRED"); });',
      'console.log("Final status set: " + jsonData.finalStatus);'
    ]
  },
  {
    name: '12. Candidate View Recruitment Status',
    description: 'Candidate views their recruitment status',
    request: {
      method: 'GET',
      header: [{ key: 'Authorization', value: 'Bearer {{candidateToken}}' }],
      body: { mode: 'raw', raw: '' },
      url: { host: ['{{baseUrl}}'], path: ['api', 'v1', 'recruitments'] }
    },
    testScript: [
      'pm.test("Status 200", function () { pm.response.to.have.status(200); });',
      'var jsonData = pm.response.json();',
      'console.log("Recruitment count: " + jsonData.length);',
      'if (jsonData.length > 0) {',
      '  var rec = jsonData[0];',
      '  console.log("Final Status: " + rec.finalStatus);',
      '  console.log("Interview Status: " + rec.interviewStatus);',
      '  pm.test("Final status is HIRED", function () { pm.expect(rec.finalStatus).to.eql("HIRED"); });',
      '}'
    ]
  }
];

const collection = {
  info: {
    name: 'SmartHire Review-1 E2E Demo',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'
  },
  item: requests.map(r => ({
    name: r.name,
    description: r.description,
    request: r.request,
    event: [
      {
        listen: 'test',
        script: {
          type: 'text/javascript',
          exec: r.testScript
        }
      }
    ]
  })),
  variable: [
    { id: '1', value: baseUrl, key: 'baseUrl' },
    { id: '2', value: '', key: 'recruiterToken' },
    { id: '3', value: '', key: 'candidateToken' },
    { id: '4', value: '', key: 'jobId' },
    { id: '5', value: '', key: 'applicationId' },
    { id: '6', value: '', key: 'recruitmentId' },
    { id: '7', value: '', key: 'candidateId' }
  ]
};

const json = JSON.stringify(collection, null, 2);
fs.writeFileSync('deploy/postman/SmartHire-Review1-E2E-Collection.json', json, 'utf8');
console.log('Postman collection created successfully');
console.log('File size:', json.length, 'bytes');
