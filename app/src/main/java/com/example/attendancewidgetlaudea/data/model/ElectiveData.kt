package com.example.attendancewidgetlaudea.data.model

data class ElectiveCourse(val code: String, val name: String)

// Professional electives by department
fun getProfessionalElectives(department: Department): List<ElectiveCourse> {
    return when (department) {
        Department.CSE -> cseProfessionalElectives
        Department.CSBS -> csbsProfessionalElectives
        Department.ECE -> eceProfessionalElectives
        Department.EEE -> eeeProfessionalElectives
        Department.MECH -> mechProfessionalElectives
        Department.CIVIL -> civilProfessionalElectives
        Department.AIDS -> aidsProfessionalElectives
    }
}

// Open electives (shared across departments with minor variations)
fun getOpenElectives(): List<ElectiveCourse> {
    return openElectives
}

// Management electives (shared across departments)
fun getManagementElectives(): List<ElectiveCourse> {
    return managementElectives
}

// All electives combined for search (regulation-aware)
fun getAllElectives(department: Department, regulation: Regulation = Regulation.R2021): List<ElectiveCourse> {
    return if (regulation == Regulation.R2025) {
        getR2025ProfessionalElectives(department).distinctBy { it.code }
    } else {
        (getProfessionalElectives(department) + getOpenElectives() + getManagementElectives())
            .distinctBy { it.code }
    }
}

// ============================================================================
// CSE Professional Electives
// ============================================================================
private val cseProfessionalElectives = listOf(
    ElectiveCourse("CCS346", "Exploratory Data Analysis"),
    ElectiveCourse("CCS360", "Recommender Systems"),
    ElectiveCourse("CCS355", "Neural Networks and Deep Learning"),
    ElectiveCourse("CCS369", "Text and Speech Analysis"),
    ElectiveCourse("CCW331", "Business Analytics"),
    ElectiveCourse("CCS349", "Image and Video Analytics"),
    ElectiveCourse("CCS338", "Computer Vision"),
    ElectiveCourse("CCS334", "Big Data Analytics"),
    ElectiveCourse("CCS375", "Web Technologies"),
    ElectiveCourse("CCS332", "App Development"),
    ElectiveCourse("CCS336", "Cloud Services Management"),
    ElectiveCourse("CCS370", "UI and UX Design"),
    ElectiveCourse("CCS366", "Software Testing and Automation"),
    ElectiveCourse("CCS374", "Web Application Security"),
    ElectiveCourse("CCS342", "DevOps"),
    ElectiveCourse("CCS358", "Principles of Programming Languages"),
    ElectiveCourse("CCS335", "Cloud Computing"),
    ElectiveCourse("CCS372", "Virtualization"),
    ElectiveCourse("CCS341", "Data Warehousing"),
    ElectiveCourse("CCS367", "Storage Technologies"),
    ElectiveCourse("CCS365", "Software Defined Networks"),
    ElectiveCourse("CCS368", "Stream Processing"),
    ElectiveCourse("CCS362", "Security and Privacy in Cloud"),
    ElectiveCourse("CCS344", "Ethical Hacking"),
    ElectiveCourse("CCS343", "Digital and Mobile Forensics"),
    ElectiveCourse("CCS363", "Social Network Security"),
    ElectiveCourse("CCS351", "Modern Cryptography"),
    ElectiveCourse("CB3591", "Engineering Secure Software Systems"),
    ElectiveCourse("CCS339", "Cryptocurrency and Blockchain Technologies"),
    ElectiveCourse("CCS354", "Network Security"),
    ElectiveCourse("CCS333", "Augmented Reality/Virtual Reality"),
    ElectiveCourse("CCS352", "Multimedia and Animation"),
    ElectiveCourse("CCS371", "Video Creation and Editing"),
    ElectiveCourse("CCW332", "Digital Marketing"),
    ElectiveCourse("CCS373", "Visual Effects"),
    ElectiveCourse("CCS347", "Game Development"),
    ElectiveCourse("CCS353", "Multimedia Data Compression and Storage"),
    ElectiveCourse("CCS361", "Robotic Process Automation"),
    ElectiveCourse("CCS340", "Cyber Security"),
    ElectiveCourse("CCS359", "Quantum Computing"),
    ElectiveCourse("CCS331", "3D Printing and Design"),
    ElectiveCourse("CCS350", "Knowledge Engineering"),
    ElectiveCourse("CCS364", "Soft Computing"),
    ElectiveCourse("CCS357", "Optimization Techniques"),
    ElectiveCourse("CCS348", "Game Theory"),
    ElectiveCourse("CCS337", "Cognitive Science"),
    ElectiveCourse("CCS345", "Ethics And AI")
)

// ============================================================================
// CSBS Professional Electives
// ============================================================================
private val csbsProfessionalElectives = listOf(
    ElectiveCourse("CCS346", "Exploratory Data Analysis"),
    ElectiveCourse("CCS360", "Recommender Systems"),
    ElectiveCourse("CCS355", "Neural Networks and Deep Learning"),
    ElectiveCourse("CCS369", "Text and Speech Analysis"),
    ElectiveCourse("CCS349", "Image and Video Analytics"),
    ElectiveCourse("CCS338", "Computer Vision"),
    ElectiveCourse("CCS334", "Big Data Analytics"),
    ElectiveCourse("CCS335", "Cloud Computing"),
    ElectiveCourse("CCS372", "Virtualization"),
    ElectiveCourse("CCS336", "Cloud Services Management"),
    ElectiveCourse("CCS341", "Data Warehousing"),
    ElectiveCourse("CCS367", "Storage Technologies"),
    ElectiveCourse("CCS365", "Software Defined Networks"),
    ElectiveCourse("CCS368", "Stream Processing"),
    ElectiveCourse("CCS362", "Security and Privacy in Cloud"),
    ElectiveCourse("CCS333", "Augmented Reality/Virtual Reality"),
    ElectiveCourse("CCS361", "Robotic Process Automation"),
    ElectiveCourse("CCS340", "Cyber Security"),
    ElectiveCourse("CCS359", "Quantum Computing"),
    ElectiveCourse("CCS339", "Cryptocurrency and Blockchain Technologies"),
    ElectiveCourse("CCS347", "Game Development"),
    ElectiveCourse("CCS331", "3D Printing and Design"),
    ElectiveCourse("CCS350", "Knowledge Engineering"),
    ElectiveCourse("CCS364", "Soft Computing"),
    ElectiveCourse("CCS357", "Optimization Techniques"),
    ElectiveCourse("CCS348", "Game Theory"),
    ElectiveCourse("CCS337", "Cognitive Science"),
    ElectiveCourse("CCS345", "Ethics and AI"),
    ElectiveCourse("CW3003", "Customer Relation Management"),
    ElectiveCourse("CMG341", "Human Resource Management for Entrepreneurs"),
    ElectiveCourse("CCD332", "Financial Management"),
    ElectiveCourse("CCD334", "Supply Chain Management"),
    ElectiveCourse("CW3007", "IT Project Management"),
    ElectiveCourse("CW3005", "Entrepreneurship Development"),
    ElectiveCourse("CW3006", "Introduction to Innovation IP Management and Entrepreneurship"),
    ElectiveCourse("CW3001", "Behavioral Economics"),
    ElectiveCourse("CMG354", "Financial Analytics"),
    ElectiveCourse("CCW332", "Digital Marketing"),
    ElectiveCourse("CW3002", "Conversational Systems"),
    ElectiveCourse("CW3009", "Social Text and Media Analytics"),
    ElectiveCourse("CCB331", "Marketing Research and Marketing Management"),
    ElectiveCourse("CW3008", "Risk Analytics"),
    ElectiveCourse("CW3004", "Enterprise Security")
)

// ============================================================================
// ECE Professional Electives
// ============================================================================
private val eceProfessionalElectives = listOf(
    ElectiveCourse("CEC363", "Wide Bandgap Devices"),
    ElectiveCourse("CEC361", "Validation and Testing Technology"),
    ElectiveCourse("CEC370", "Low Power IC Design"),
    ElectiveCourse("CEC362", "VLSI Testing and Design For Testability"),
    ElectiveCourse("CEC342", "Mixed Signal IC Design Testing"),
    ElectiveCourse("CEC334", "Analog IC Design"),
    ElectiveCourse("CEC332", "Advanced Digital Signal Processing"),
    ElectiveCourse("CEC366", "Image Processing"),
    ElectiveCourse("CEC356", "Speech Processing"),
    ElectiveCourse("CEC355", "Software Defined Radio"),
    ElectiveCourse("CEC337", "DSP Architecture and Programming"),
    ElectiveCourse("CCS338", "Computer Vision"),
    ElectiveCourse("CEC350", "RF Transceivers"),
    ElectiveCourse("CEC353", "Signal Integrity"),
    ElectiveCourse("CEC335", "Antenna Design"),
    ElectiveCourse("CEC341", "MICs and RF System Design"),
    ElectiveCourse("CEC338", "EMI/EMC Pre Compliance Testing"),
    ElectiveCourse("CEC349", "RFID System Design and Testing"),
    ElectiveCourse("CBM370", "Wearable Devices"),
    ElectiveCourse("CBM352", "Human Assist Devices"),
    ElectiveCourse("CBM368", "Therapeutic Equipment"),
    ElectiveCourse("CBM355", "Medical Imaging Systems"),
    ElectiveCourse("CBM342", "Brain Computer Interface and Applications"),
    ElectiveCourse("CBM341", "Body Area Networks"),
    ElectiveCourse("CEC359", "Underwater Instrumentation System"),
    ElectiveCourse("CEC358", "Underwater Imaging Systems and Image Processing"),
    ElectiveCourse("CEC357", "Underwater Communication"),
    ElectiveCourse("CEC344", "Ocean Observation Systems"),
    ElectiveCourse("CEC360", "Underwater Navigation Systems"),
    ElectiveCourse("CEC343", "Ocean Acoustics"),
    ElectiveCourse("CEC369", "IoT Processors"),
    ElectiveCourse("CEC368", "IoT Based Systems Design"),
    ElectiveCourse("CEC365", "Wireless Sensor Network Design"),
    ElectiveCourse("CEC367", "Industrial IoT and Industry 4.0"),
    ElectiveCourse("CEC340", "MEMS Design"),
    ElectiveCourse("CEC339", "Fundamentals of Nanoelectronics"),
    ElectiveCourse("CEC347", "Radar Technologies"),
    ElectiveCourse("CEC336", "Avionics Systems"),
    ElectiveCourse("CEC346", "Positioning and Navigation Systems"),
    ElectiveCourse("CEC352", "Satellite Communication"),
    ElectiveCourse("CEC348", "Remote Sensing"),
    ElectiveCourse("CEC351", "Rocketry and Space Mechanics"),
    ElectiveCourse("CEC345", "Optical Communication and Networks"),
    ElectiveCourse("CEC364", "Wireless Broad Band Networks"),
    ElectiveCourse("CEC331", "4G/5G Communication Networks"),
    ElectiveCourse("CEC354", "Software Defined Networks"),
    ElectiveCourse("CEC371", "Massive MIMO Networks"),
    ElectiveCourse("CEC333", "Advanced Wireless Communication Techniques")
)

// ============================================================================
// EEE Professional Electives
// ============================================================================
private val eeeProfessionalElectives = listOf(
    ElectiveCourse("EE3001", "Utilization and Conservation of Electrical Energy"),
    ElectiveCourse("EE3002", "Under Ground Cable Engineering"),
    ElectiveCourse("EE3003", "Substation Engineering and Automation"),
    ElectiveCourse("EE3004", "HVDC and FACTS"),
    ElectiveCourse("EE3005", "Energy Management and Auditing"),
    ElectiveCourse("EE3006", "Power Quality"),
    ElectiveCourse("EE3007", "Smart Grid"),
    ElectiveCourse("EE3008", "Restructured Power Market"),
    ElectiveCourse("EE3009", "Special Electrical Machines"),
    ElectiveCourse("EE3010", "Analysis of Electrical Machines"),
    ElectiveCourse("EE3011", "Multilevel Power Converters"),
    ElectiveCourse("EE3012", "Electrical Drives"),
    ElectiveCourse("EE3013", "SMPS and UPS"),
    ElectiveCourse("EE3014", "Power Electronics for Renewable Energy Systems"),
    ElectiveCourse("EE3015", "Control of Power Electronics Circuits"),
    ElectiveCourse("EE3016", "Embedded System Design"),
    ElectiveCourse("EE3017", "Embedded C-programming"),
    ElectiveCourse("EE3018", "Embedded Processors"),
    ElectiveCourse("EE3019", "Embedded Control for Electric Drives"),
    ElectiveCourse("EE3020", "Smart System Automation"),
    ElectiveCourse("EE3021", "Embedded System for Automotive Applications"),
    ElectiveCourse("EE3022", "VLSI Design"),
    ElectiveCourse("EE3023", "MEMS and NEMS"),
    ElectiveCourse("EE3024", "Digital Signal Processing System Design"),
    ElectiveCourse("EE3025", "Electric Vehicle Architecture"),
    ElectiveCourse("EE3026", "Design of Motor and Power Converters for Electric Vehicles"),
    ElectiveCourse("EE3027", "Electric Vehicle Design Mechanics and Control"),
    ElectiveCourse("EE3028", "Design of Electric Vehicle Charging System"),
    ElectiveCourse("EE3029", "Testing of Electric Vehicles"),
    ElectiveCourse("EE3030", "Grid Integration of Electric Vehicles"),
    ElectiveCourse("EE3031", "Intelligent Control of Electric Vehicles"),
    ElectiveCourse("CIC331", "Process Modeling and Simulation"),
    ElectiveCourse("CIC332", "Computer Control of Processes"),
    ElectiveCourse("CIC333", "System Identification"),
    ElectiveCourse("CIC336", "Model Based Control"),
    ElectiveCourse("CIC334", "Non Linear Control"),
    ElectiveCourse("CIC337", "Optimal Control"),
    ElectiveCourse("CIC335", "Adaptive Control"),
    ElectiveCourse("CIC338", "Machine Monitoring System"),
    ElectiveCourse("EE3032", "Energy Storage Systems"),
    ElectiveCourse("EE3033", "Hybrid Energy Technology"),
    ElectiveCourse("EE3034", "Design and Modeling of Renewable Energy Systems"),
    ElectiveCourse("EE3035", "Grid Integrating Techniques and Challenges"),
    ElectiveCourse("EE3036", "Sustainable and Environmental Friendly HV Insulation System"),
    ElectiveCourse("EE3037", "Power System Transients"),
    ElectiveCourse("CEI331", "PLC Programming"),
    ElectiveCourse("CCS334", "Big Data Analytics")
)

// ============================================================================
// MECH Professional Electives
// ============================================================================
private val mechProfessionalElectives = listOf(
    ElectiveCourse("CME331", "Automotive Materials Components Design and Testing"),
    ElectiveCourse("CME332", "Conventional and Futuristic Vehicle Technology"),
    ElectiveCourse("CME333", "Renewable Powered Off Highway Vehicles and Emission Control Technology"),
    ElectiveCourse("CME334", "Vehicle Health Monitoring Maintenance and Safety"),
    ElectiveCourse("CME335", "CAE and CFD Approach in Future Mobility"),
    ElectiveCourse("CME336", "Hybrid and Electric Vehicle Technology"),
    ElectiveCourse("CME337", "Thermal Management of Batteries and Fuel Cells"),
    ElectiveCourse("CME338", "Value Engineering"),
    ElectiveCourse("CME339", "Additive Manufacturing"),
    ElectiveCourse("CME340", "CAD/CAM"),
    ElectiveCourse("CME341", "Design For X"),
    ElectiveCourse("CME342", "Ergonomics in Design"),
    ElectiveCourse("CME343", "New Product Development"),
    ElectiveCourse("CME344", "Product Life Cycle Management"),
    ElectiveCourse("MR3491", "Sensors and Instrumentation"),
    ElectiveCourse("MR3392", "Electrical Drives and Actuators"),
    ElectiveCourse("MR3492", "Embedded Systems and Programming"),
    ElectiveCourse("MR3691", "Robotics"),
    ElectiveCourse("CMR338", "Smart Mobility and Intelligent Vehicles"),
    ElectiveCourse("CME345", "Haptics and Immersive Technologies"),
    ElectiveCourse("CRA332", "Drone Technologies"),
    ElectiveCourse("CME346", "Digital Manufacturing and IoT"),
    ElectiveCourse("CME347", "Lean Manufacturing"),
    ElectiveCourse("CME348", "Modern Robotics"),
    ElectiveCourse("CME349", "Green Manufacturing Design and Practices"),
    ElectiveCourse("CME350", "Environment Sustainability and Impact Assessment"),
    ElectiveCourse("CME351", "Energy Saving Machinery and Components"),
    ElectiveCourse("CME352", "Green Supply Chain Management"),
    ElectiveCourse("CME353", "Design of Pressure Vessels"),
    ElectiveCourse("CME354", "Failure Analysis and NDT Techniques"),
    ElectiveCourse("CME355", "Material Handling and Solid Processing Equipment"),
    ElectiveCourse("CME356", "Rotating Machinery Design"),
    ElectiveCourse("CME357", "Thermal and Fired Equipment Design"),
    ElectiveCourse("CME358", "Industrial Layout Design and Safety"),
    ElectiveCourse("CME359", "Design Codes and Standards"),
    ElectiveCourse("CME360", "Bioenergy Conversion Technologies"),
    ElectiveCourse("CME361", "Carbon Footprint Estimation and Reduction Techniques"),
    ElectiveCourse("CME362", "Energy Conservation in Industries"),
    ElectiveCourse("CME363", "Energy Efficient Buildings"),
    ElectiveCourse("CME364", "Energy Storage Devices"),
    ElectiveCourse("CME365", "Renewable Energy Technologies"),
    ElectiveCourse("CME366", "Equipment for Pollution Control"),
    ElectiveCourse("CME367", "Computational Solid Mechanics"),
    ElectiveCourse("CME368", "Computational Fluid Dynamics and Heat Transfer"),
    ElectiveCourse("CME369", "Theory on Computation and Visualization"),
    ElectiveCourse("CME370", "Computational Bio-Mechanics"),
    ElectiveCourse("CME371", "Advanced Statistics and Data Analytics"),
    ElectiveCourse("CME372", "CAD and CAE"),
    ElectiveCourse("CRA342", "Machine Learning for Intelligent Systems"),
    ElectiveCourse("CME380", "Automobile Engineering"),
    ElectiveCourse("ME3001", "Measurements and Controls"),
    ElectiveCourse("CME381", "Design Concepts in Engineering"),
    ElectiveCourse("CME382", "Composite Materials and Mechanics"),
    ElectiveCourse("CME383", "Electrical Drives and Control"),
    ElectiveCourse("CME384", "Power Plant Engineering"),
    ElectiveCourse("CME385", "Refrigeration and Air Conditioning"),
    ElectiveCourse("CAU332", "Dynamics of Ground Vehicles"),
    ElectiveCourse("CAE353", "Turbo Machines"),
    ElectiveCourse("CME387", "Non-traditional Machining Processes"),
    ElectiveCourse("CME388", "Industrial Safety"),
    ElectiveCourse("CME389", "Design of Transmission System"),
    ElectiveCourse("CME390", "Thermal Power Engineering"),
    ElectiveCourse("CME391", "Design for Manufacturing"),
    ElectiveCourse("CME392", "Power Generation Equipment Design"),
    ElectiveCourse("CME393", "Advanced Vehicle Engineering"),
    ElectiveCourse("CME394", "Advanced Internal Combustion Engineering"),
    ElectiveCourse("CME395", "Casting and Welding Processes"),
    ElectiveCourse("CME396", "Process Planning and Cost Estimation"),
    ElectiveCourse("CME397", "Surface Engineering"),
    ElectiveCourse("CME398", "Precision Manufacturing"),
    ElectiveCourse("CME386", "Gas Dynamics and Jet Propulsion"),
    ElectiveCourse("CME399", "Operational Research")
)

// ============================================================================
// CIVIL Professional Electives
// ============================================================================
private val civilProfessionalElectives = listOf(
    ElectiveCourse("CE3001", "Concrete Structures"),
    ElectiveCourse("CE3002", "Steel Structures"),
    ElectiveCourse("CE3003", "Prefabricated Structures"),
    ElectiveCourse("CE3004", "Prestressed Concrete Structures"),
    ElectiveCourse("CE3005", "Rehabilitation/Heritage Restoration"),
    ElectiveCourse("CE3006", "Dynamics and Earthquake Resistant Structures"),
    ElectiveCourse("CE3007", "Introduction to Finite Element Method"),
    ElectiveCourse("CE3008", "Formwork Engineering"),
    ElectiveCourse("CE3009", "Construction Equipment and Machinery"),
    ElectiveCourse("CE3010", "Sustainable Construction And Lean Construction"),
    ElectiveCourse("CE3011", "Digitalized Construction Lab"),
    ElectiveCourse("CE3012", "Construction Management and Safety"),
    ElectiveCourse("CE3013", "Advanced Construction Techniques"),
    ElectiveCourse("CE3014", "Energy Efficient Buildings"),
    ElectiveCourse("CE3015", "Geoenvironmental Engineering"),
    ElectiveCourse("CE3016", "Ground Improvement Techniques"),
    ElectiveCourse("CE3017", "Soil Dynamics and Machine Foundations"),
    ElectiveCourse("CE3018", "Rock Mechanics"),
    ElectiveCourse("CE3019", "Earth and Earth Retaining Structures"),
    ElectiveCourse("CE3020", "Pile Foundation"),
    ElectiveCourse("CE3021", "Tunneling Engineering"),
    ElectiveCourse("GI3492", "Total Station and GPS Surveying"),
    ElectiveCourse("CE3022", "Remote Sensing Concepts"),
    ElectiveCourse("CE3023", "Satellite Image Processing"),
    ElectiveCourse("GI3491", "Cartography and GIS"),
    ElectiveCourse("GI3391", "Photogrammetry"),
    ElectiveCourse("GI3691", "Airborne and Terrestrial Laser Mapping"),
    ElectiveCourse("CE3024", "Hydrographic Surveying"),
    ElectiveCourse("CE3025", "Airports and Harbours"),
    ElectiveCourse("CE3026", "Traffic Engineering and Management"),
    ElectiveCourse("CE3027", "Urban Planning and Development"),
    ElectiveCourse("CE3028", "Smart Cities"),
    ElectiveCourse("CE3029", "Intelligent Transportation Systems"),
    ElectiveCourse("CE3030", "Pavement Engineering"),
    ElectiveCourse("CE3031", "Transportation Planning Process"),
    ElectiveCourse("CE3032", "Climate Change Adaptation and Mitigation"),
    ElectiveCourse("CCE331", "Air and Noise Pollution Control Engineering"),
    ElectiveCourse("CCE333", "Environmental Impact Assessment"),
    ElectiveCourse("CCE334", "Industrial Wastewater Management"),
    ElectiveCourse("CE3033", "Solid and Hazardous Waste Management"),
    ElectiveCourse("CE3034", "Environmental Policy and Legislations"),
    ElectiveCourse("CCE332", "Environmental Health and Safety"),
    ElectiveCourse("CE3035", "Irrigation Engineering and Drawing"),
    ElectiveCourse("CE3036", "Ground Water Engineering"),
    ElectiveCourse("CE3037", "Water Resources Systems Engineering"),
    ElectiveCourse("CE3038", "Watershed Conservation and Management"),
    ElectiveCourse("CE3039", "Integrated Water Resources Management"),
    ElectiveCourse("CE3040", "Urban Water Infrastructure"),
    ElectiveCourse("CE3041", "Water Quality and Management"),
    ElectiveCourse("CE3042", "Ocean Wave Dynamics"),
    ElectiveCourse("CE3043", "Marine Geotechnical Engineering"),
    ElectiveCourse("CE3044", "Coastal Engineering"),
    ElectiveCourse("CE3045", "Offshore Structures"),
    ElectiveCourse("CE3046", "Port and Harbour Engineering"),
    ElectiveCourse("CE3047", "Coastal Hazards and Mitigation"),
    ElectiveCourse("CE3048", "Coastal Zone Management and Remote Sensing"),
    ElectiveCourse("CE3049", "Steel Concrete Composite Structures"),
    ElectiveCourse("CE3050", "Finance for Engineers"),
    ElectiveCourse("CE3051", "Earth and Rockfill Dams"),
    ElectiveCourse("CE3052", "Computational Fluid Dynamics"),
    ElectiveCourse("CE3053", "Rainwater Harvesting"),
    ElectiveCourse("CE3054", "Transport and Environment"),
    ElectiveCourse("CE3055", "Environmental Quality Monitoring")
)

// ============================================================================
// AIDS Professional Electives
// ============================================================================
private val aidsProfessionalElectives = listOf(
    ElectiveCourse("CCS350", "Knowledge Engineering"),
    ElectiveCourse("CCS360", "Recommender Systems"),
    ElectiveCourse("CCS364", "Soft Computing"),
    ElectiveCourse("CCS369", "Text and Speech Analysis"),
    ElectiveCourse("CCW331", "Business Analytics"),
    ElectiveCourse("CCS349", "Image and Video Analytics"),
    ElectiveCourse("CCS338", "Computer Vision"),
    ElectiveCourse("CCS335", "Cloud Computing"),
    ElectiveCourse("CCS332", "App Development"),
    ElectiveCourse("CCS336", "Cloud Services Management"),
    ElectiveCourse("CCS370", "UI and UX Design"),
    ElectiveCourse("CCS366", "Software Testing and Automation"),
    ElectiveCourse("CCS374", "Web Application Security"),
    ElectiveCourse("CCS342", "DevOps"),
    ElectiveCourse("CCS358", "Principles of Programming Languages"),
    ElectiveCourse("CCS372", "Virtualization"),
    ElectiveCourse("CCS341", "Data Warehousing"),
    ElectiveCourse("CCS367", "Storage Technologies"),
    ElectiveCourse("CCS365", "Software Defined Networks"),
    ElectiveCourse("CCS368", "Stream Processing"),
    ElectiveCourse("CCS362", "Security and Privacy in Cloud"),
    ElectiveCourse("CCS344", "Ethical Hacking"),
    ElectiveCourse("CCS343", "Digital and Mobile Forensics"),
    ElectiveCourse("CCS363", "Social Network Security"),
    ElectiveCourse("CCS351", "Modern Cryptography"),
    ElectiveCourse("CB3591", "Engineering Secure Software Systems"),
    ElectiveCourse("CCS339", "Cryptocurrency and Blockchain Technologies"),
    ElectiveCourse("CCS354", "Network Security"),
    ElectiveCourse("CCS333", "Augmented Reality/Virtual Reality"),
    ElectiveCourse("CCS352", "Multimedia and Animation"),
    ElectiveCourse("CCS371", "Video Creation and Editing"),
    ElectiveCourse("CCW332", "Digital Marketing"),
    ElectiveCourse("CCS353", "Multimedia Data Compression and Storage"),
    ElectiveCourse("CCS373", "Visual Effects"),
    ElectiveCourse("CCS347", "Game Development"),
    ElectiveCourse("CCS361", "Robotic Process Automation"),
    ElectiveCourse("CCS355", "Neural Networks and Deep Learning"),
    ElectiveCourse("CCS340", "Cyber Security"),
    ElectiveCourse("CCS359", "Quantum Computing"),
    ElectiveCourse("CCS331", "3D Printing and Design"),
    ElectiveCourse("AD3001", "Bio-Inspired Optimization Techniques"),
    ElectiveCourse("AD3002", "Health Care Analytics"),
    ElectiveCourse("CCS357", "Optimization Techniques"),
    ElectiveCourse("CCS348", "Game Theory"),
    ElectiveCourse("CCS337", "Cognitive Science"),
    ElectiveCourse("CCS345", "Ethics and AI")
)

// ============================================================================
// Open Electives (shared across all departments)
// ============================================================================
private val openElectives = listOf(
    ElectiveCourse("OAS351", "Space Science"),
    ElectiveCourse("OIE351", "Introduction to Industrial Engineering"),
    ElectiveCourse("OBT351", "Food Nutrition and Health"),
    ElectiveCourse("OCE351", "Environmental and Social Impact Assessment"),
    ElectiveCourse("OEE351", "Renewable Energy System"),
    ElectiveCourse("OEI351", "Introduction to Industrial Instrumentation and Control"),
    ElectiveCourse("OMA351", "Graph Theory"),
    ElectiveCourse("OIE352", "Resource Management Techniques"),
    ElectiveCourse("OMG351", "Fintech Regulation"),
    ElectiveCourse("OFD351", "Holistic Nutrition"),
    ElectiveCourse("AI3021", "IT in Agricultural System"),
    ElectiveCourse("OEI352", "Introduction to Control Engineering"),
    ElectiveCourse("OPY351", "Pharmaceutical Nanotechnology"),
    ElectiveCourse("OAE351", "Aviation Management"),
    ElectiveCourse("OHS351", "English for Competitive Examinations"),
    ElectiveCourse("OMG352", "NGOs and Sustainable Development"),
    ElectiveCourse("OMG353", "Democracy and Good Governance"),
    ElectiveCourse("CME365", "Renewable Energy Technologies"),
    ElectiveCourse("OME354", "Applied Design Thinking"),
    ElectiveCourse("MF3003", "Reverse Engineering"),
    ElectiveCourse("OPR351", "Sustainable Manufacturing"),
    ElectiveCourse("AU3791", "Electric and Hybrid Vehicles"),
    ElectiveCourse("OAS352", "Space Engineering"),
    ElectiveCourse("OIM351", "Industrial Management"),
    ElectiveCourse("OIE354", "Quality Engineering"),
    ElectiveCourse("OSF351", "Fire Safety Engineering"),
    ElectiveCourse("OML351", "Introduction to Non-destructive Testing"),
    ElectiveCourse("OMR351", "Mechatronics"),
    ElectiveCourse("ORA351", "Foundation of Robotics"),
    ElectiveCourse("OAE352", "Fundamentals of Aeronautical Engineering"),
    ElectiveCourse("OGI351", "Remote Sensing Concepts"),
    ElectiveCourse("OAI351", "Urban Agriculture"),
    ElectiveCourse("OEN351", "Drinking Water Supply and Treatment"),
    ElectiveCourse("OEE352", "Electric Vehicle Technology"),
    ElectiveCourse("OEI353", "Introduction to PLC Programming"),
    ElectiveCourse("OCH351", "Nano Technology"),
    ElectiveCourse("OCH352", "Functional Materials"),
    ElectiveCourse("OFD352", "Traditional Indian Foods"),
    ElectiveCourse("OFD353", "Introduction to Food Processing"),
    ElectiveCourse("OPY352", "IPR for Pharma Industry"),
    ElectiveCourse("OTT351", "Basics of Textile Finishing"),
    ElectiveCourse("OTT352", "Industrial Engineering for Garment Industry"),
    ElectiveCourse("OTT353", "Basics of Textile Manufacture"),
    ElectiveCourse("OPE351", "Introduction to Petroleum Refining and Petrochemicals"),
    ElectiveCourse("CPE334", "Energy Conservation and Management"),
    ElectiveCourse("OPT351", "Basics of Plastics Processing"),
    ElectiveCourse("OEC351", "Signals and Systems"),
    ElectiveCourse("OEC352", "Fundamentals of Electronic Devices and Circuits"),
    ElectiveCourse("CBM348", "Foundation Skills in Integrated Product Development"),
    ElectiveCourse("CBM333", "Assistive Technology"),
    ElectiveCourse("OMA352", "Operations Research"),
    ElectiveCourse("OMA353", "Algebra and Number Theory"),
    ElectiveCourse("OMA354", "Linear Algebra"),
    ElectiveCourse("OCE353", "Lean Concepts Tools and Practices"),
    ElectiveCourse("OBT352", "Basics of Microbial Technology"),
    ElectiveCourse("OBT353", "Basics of Biomolecules"),
    ElectiveCourse("OBT354", "Fundamentals of Cell and Molecular Biology"),
    ElectiveCourse("OHS352", "Project Report Writing"),
    ElectiveCourse("OMA355", "Advanced Numerical Methods"),
    ElectiveCourse("OMA356", "Random Processes"),
    ElectiveCourse("OMA357", "Queuing and Reliability Modelling"),
    ElectiveCourse("OMG354", "Production and Operations Management for Entrepreneurs"),
    ElectiveCourse("OMG355", "Multivariate Data Analysis"),
    ElectiveCourse("OME352", "Additive Manufacturing"),
    ElectiveCourse("CME343", "New Product Development"),
    ElectiveCourse("OME355", "Industrial Design and Rapid Prototyping Techniques"),
    ElectiveCourse("MF3010", "Micro and Precision Engineering"),
    ElectiveCourse("OMF354", "Cost Management of Engineering Projects"),
    ElectiveCourse("AU3002", "Batteries and Management System"),
    ElectiveCourse("AU3008", "Sensors and Actuators"),
    ElectiveCourse("OAS353", "Space Vehicles"),
    ElectiveCourse("OIM352", "Management Science"),
    ElectiveCourse("OIM353", "Production Planning and Control"),
    ElectiveCourse("OIE353", "Operations Management"),
    ElectiveCourse("OSF352", "Industrial Hygiene"),
    ElectiveCourse("OSF353", "Chemical Process Safety"),
    ElectiveCourse("OML352", "Electrical Electronic and Magnetic Materials"),
    ElectiveCourse("OML353", "Nanomaterials and Applications"),
    ElectiveCourse("OMR352", "Hydraulics and Pneumatics"),
    ElectiveCourse("OMR353", "Sensors"),
    ElectiveCourse("ORA352", "Concepts in Mobile Robots"),
    ElectiveCourse("MV3501", "Marine Propulsion"),
    ElectiveCourse("OMV351", "Marine Merchant Vessels"),
    ElectiveCourse("OMV352", "Elements of Marine Engineering"),
    ElectiveCourse("CRA332", "Drone Technologies"),
    ElectiveCourse("OGI352", "Geographical Information System"),
    ElectiveCourse("OAI352", "Agriculture Entrepreneurship Development"),
    ElectiveCourse("OEN352", "Biodiversity Conservation"),
    ElectiveCourse("OEE353", "Introduction to Control Systems"),
    ElectiveCourse("OEI354", "Introduction to Industrial Automation Systems"),
    ElectiveCourse("OCH353", "Energy Technology"),
    ElectiveCourse("OCH354", "Surface Science"),
    ElectiveCourse("OFD354", "Fundamentals of Food Engineering"),
    ElectiveCourse("OFD355", "Food Safety and Quality Regulations"),
    ElectiveCourse("OPY353", "Nutraceuticals"),
    ElectiveCourse("OTT354", "Basics of Dyeing and Printing"),
    ElectiveCourse("FT3201", "Fibre Science"),
    ElectiveCourse("OTT355", "Garment Manufacturing Technology"),
    ElectiveCourse("OPE353", "Industrial Safety"),
    ElectiveCourse("OPE354", "Unit Operations in Petro Chemical Industries"),
    ElectiveCourse("OPT352", "Plastic Materials for Engineers"),
    ElectiveCourse("OPT353", "Properties and Testing of Plastics"),
    ElectiveCourse("OEC353", "VLSI Design"),
    ElectiveCourse("CBM370", "Wearable Devices"),
    ElectiveCourse("CBM356", "Medical Informatics"),
    ElectiveCourse("OCE354", "Basics of Integrated Water Resources Management"),
    ElectiveCourse("OBT355", "Biotechnology for Waste Management"),
    ElectiveCourse("OBT356", "Lifestyle Diseases"),
    ElectiveCourse("OBT357", "Biotechnology in Health Care"),
    ElectiveCourse("OCS351", "Artificial Intelligence and Machine Learning Fundamentals"),
    ElectiveCourse("OCS352", "IoT Concepts and Applications"),
    ElectiveCourse("OCS353", "Data Science Fundamentals"),
    ElectiveCourse("CCS355", "Neural Networks and Deep Learning"),
    ElectiveCourse("CCS342", "DevOps"),
    ElectiveCourse("CCS361", "Robotic Process Automation")
)

// ============================================================================
// Management Electives (shared across all departments)
// ============================================================================
private val managementElectives = listOf(
    ElectiveCourse("GE3751", "Principles of Management"),
    ElectiveCourse("GE3752", "Total Quality Management"),
    ElectiveCourse("GE3753", "Engineering Economics and Financial Accounting"),
    ElectiveCourse("GE3754", "Human Resource Management"),
    ElectiveCourse("GE3755", "Knowledge Management"),
    ElectiveCourse("GE3792", "Industrial Management")
)

// ============================================================================
// R2025 REGULATION - Professional Elective Courses (PSGiTech Autonomous)
// ============================================================================

// R2025 Professional Electives by department
fun getR2025ProfessionalElectives(department: Department): List<ElectiveCourse> {
    return when (department) {
        Department.CSE -> r2025CseProfessionalElectives
        Department.CSBS -> r2025CseProfessionalElectives // No CSBS in R2025, fall back to CSE
        Department.ECE -> r2025EceProfessionalElectives
        Department.EEE -> r2025EeeProfessionalElectives
        Department.MECH -> r2025MechProfessionalElectives
        Department.CIVIL -> r2025CivilProfessionalElectives
        Department.AIDS -> r2025AidsProfessionalElectives
    }
}

// ============================================================================
// R2025 CSE Professional Electives (4 Verticals)
// Vertical I: Full Stack Development
// Vertical II: Cyber Physical Systems
// Vertical III: Artificial Intelligence and Data Science
// Vertical IV: Frontier Technologies
// ============================================================================
private val r2025CseProfessionalElectives = listOf(
    // Vertical I - Full Stack Development
    ElectiveCourse("25CSP01", "Micro Service Architecture"),
    ElectiveCourse("25CSP02", "User Experience Design"),
    ElectiveCourse("25CSP03", "DevOps"),
    ElectiveCourse("25CSP04", "Software Testing and Automation"),
    ElectiveCourse("25CSP05", "Secure Full Stack Development"),
    ElectiveCourse("25CSP06", "MERN Stack"),
    ElectiveCourse("25CSP07", "Agile Methodologies"),
    ElectiveCourse("25CSP08", "Vibe Coding"),
    // Vertical II - Cyber Physical Systems
    ElectiveCourse("25CSP09", "Software Defined Network"),
    ElectiveCourse("25CSP10", "Security and Privacy in Cloud"),
    ElectiveCourse("25CSP11", "Virtualization"),
    ElectiveCourse("25CSP12", "Ethical Hacking"),
    ElectiveCourse("25CSP13", "Modern Cryptography"),
    ElectiveCourse("25CSP14", "Cyber Forensics"),
    ElectiveCourse("25CSP15", "AI in Cyber Security"),
    ElectiveCourse("25ADP18", "Cloud Services Management"),
    // Vertical III - Artificial Intelligence and Data Science
    ElectiveCourse("25CSP16", "Data Exploration and Visualization"),
    ElectiveCourse("25CSP17", "Optimization Techniques"),
    ElectiveCourse("25CSP18", "Data Science Essentials"),
    ElectiveCourse("25CSP19", "Reinforcement Learning Techniques"),
    ElectiveCourse("25CSP20", "Managing Big Data"),
    ElectiveCourse("25ADP09", "Natural Language Processing"),
    ElectiveCourse("25ADP05", "Text and Speech Analysis"),
    ElectiveCourse("25ADP04", "Image and Video Analytics"),
    // Vertical IV - Frontier Technologies
    ElectiveCourse("25CSP21", "Blockchain Technologies"),
    ElectiveCourse("25CSP22", "Immersive Technologies"),
    ElectiveCourse("25CSP23", "Computer Vision and Image Processing"),
    ElectiveCourse("25CSP24", "Generative AI"),
    ElectiveCourse("25ADP19", "Quantum Computing"),
    ElectiveCourse("25CSP26", "Neural Networks and Deep Learning"),
    ElectiveCourse("25ADP15", "Responsible AI"),
    ElectiveCourse("25ADP03", "Smart Systems")
)

// ============================================================================
// R2025 ECE Professional Electives (4 Verticals)
// Vertical I: VLSI Design and Testing
// Vertical II: Signal Processing and Wireless Technologies
// Vertical III: IoT and Embedded Systems
// Vertical IV: Emerging Technologies
// ============================================================================
private val r2025EceProfessionalElectives = listOf(
    // Vertical I - VLSI Design and Testing
    ElectiveCourse("25ECP01", "Mixed Signal IC Design"),
    ElectiveCourse("25ECP02", "CAD for VLSI"),
    ElectiveCourse("25ECP03", "Low Power IC Design"),
    ElectiveCourse("25ECP04", "VLSI Signal Processing"),
    ElectiveCourse("25ECP05", "CMOS Analog IC Design"),
    ElectiveCourse("25ECP06", "VLSI Testing and Design for Testability"),
    ElectiveCourse("25ECP07", "Digital Design Verification"),
    ElectiveCourse("25ECP08", "VLSI Architectures for AI Applications"),
    // Vertical II - Signal Processing and Wireless Technologies
    ElectiveCourse("25ECP11", "Advanced Digital Signal Processing"),
    ElectiveCourse("25ECP12", "Digital Image Processing"),
    ElectiveCourse("25ECP13", "Speech Processing"),
    ElectiveCourse("25ECP14", "Software Defined Radio"),
    ElectiveCourse("25ECP15", "Wavelets and Its Applications"),
    ElectiveCourse("25ECP16", "Biomedical Signal Processing"),
    ElectiveCourse("25ECP17", "5G and Beyond"),
    ElectiveCourse("25ECP18", "Mobile Communication"),
    // Vertical III - IoT and Embedded Systems
    ElectiveCourse("25ECP21", "Real Time Operating Systems"),
    ElectiveCourse("25ECP22", "IoT Based System Design"),
    ElectiveCourse("25ECP23", "Artificial IoT"),
    ElectiveCourse("25ECP24", "Industrial Internet of Things and Industry 4.0"),
    ElectiveCourse("25ECP25", "FPGA Based Embedded Systems"),
    ElectiveCourse("25ECP26", "Robotics"),
    ElectiveCourse("25ECP27", "Wearable Devices"),
    ElectiveCourse("25ECP28", "IoT Processors"),
    // Vertical IV - Emerging Technologies
    ElectiveCourse("25ECP31", "IC Packaging and Electromagnetic Interference and Compatibility"),
    ElectiveCourse("25ECP32", "Quantum Computing"),
    ElectiveCourse("25ECP33", "Artificial Intelligence and Machine Learning"),
    ElectiveCourse("25ECP34", "Cryptography and Network Security"),
    ElectiveCourse("25ECP35", "Computer and Machine Vision"),
    ElectiveCourse("25ECP36", "Avionics"),
    ElectiveCourse("25ECP37", "Natural Language Processing"),
    ElectiveCourse("25ECP38", "Information Theory and Coding")
)

// ============================================================================
// R2025 EEE Professional Electives (5 Verticals)
// Vertical 1: Embedded Systems
// Vertical 2: Power Electronics and Drives
// Vertical 3: Electric Vehicle Technology
// Vertical 4: Power System
// Vertical 5: Diversified Courses
// ============================================================================
private val r2025EeeProfessionalElectives = listOf(
    // Vertical 1 - Embedded Systems
    ElectiveCourse("25EEP01", "Embedded System Design"),
    ElectiveCourse("25EEP02", "Embedded Networks"),
    ElectiveCourse("25EEP03", "Internet of Things and Its Applications"),
    ElectiveCourse("25EEP04", "Operating Systems"),
    ElectiveCourse("25EEP05", "Computer Architecture"),
    ElectiveCourse("25EEP06", "System Design Using FPGA"),
    ElectiveCourse("25EEP07", "Digital Image Processing"),
    ElectiveCourse("25EEP08", "Automotive Electronics"),
    // Vertical 2 - Power Electronics and Drives
    ElectiveCourse("25EEP09", "Modelling and Simulation of Power Converters"),
    ElectiveCourse("25EEP10", "Switched Mode Power Conversion"),
    ElectiveCourse("25EEP11", "Control of Power Electronic Circuits"),
    ElectiveCourse("25EEP12", "Special Electrical Machine"),
    ElectiveCourse("25EEP13", "Power Electronics for Renewable Energy Systems"),
    ElectiveCourse("25EEP14", "Multilevel Power Converters"),
    ElectiveCourse("25EEP15", "Dynamic Modelling, Analysis and Design of Drives"),
    ElectiveCourse("25EEP16", "Embedded Control of Electric Drives"),
    // Vertical 3 - Electric Vehicle Technology
    ElectiveCourse("25EEP17", "Electric Vehicle Architecture"),
    ElectiveCourse("25EEP18", "Design of Motors and Converters for Electric Vehicle"),
    ElectiveCourse("25EEP19", "Intelligent Control of Electric Vehicle"),
    ElectiveCourse("25EEP20", "Battery Management System"),
    ElectiveCourse("25EEP21", "Design of Electric Vehicle Charging System"),
    ElectiveCourse("25EEP22", "Testing of Electric Vehicles"),
    ElectiveCourse("25EEP23", "Grid Integration of Electric Vehicles"),
    ElectiveCourse("25EEP24", "Artificial Intelligence for Autonomous Vehicles"),
    // Vertical 4 - Power System
    ElectiveCourse("25EEP25", "Power System Operation and Control"),
    ElectiveCourse("25EEP26", "Renewable Energy Systems"),
    ElectiveCourse("25EEP27", "Smart Grid"),
    ElectiveCourse("25EEP28", "Energy Management and Auditing"),
    ElectiveCourse("25EEP29", "Utilization and Conservation of Electrical Energy"),
    ElectiveCourse("25EEP30", "HVDC and FACTS"),
    ElectiveCourse("25EEP31", "Power Quality and Management"),
    ElectiveCourse("25EEP32", "High Voltage Engineering"),
    // Vertical 5 - Diversified Courses
    ElectiveCourse("25EEP33", "Artificial Intelligence and Machine Learning Fundamentals"),
    ElectiveCourse("25EEP34", "Data Analytics"),
    ElectiveCourse("25EEP35", "VLSI Design Techniques"),
    ElectiveCourse("25EEP36", "Cyber Security"),
    ElectiveCourse("25ICP12", "Virtual Instrumentation"),
    ElectiveCourse("25EEP37", "PLC and SCADA"),
    ElectiveCourse("25EEP38", "Electrical System Estimation and Costing"),
    ElectiveCourse("25EEP39", "Model Based Systems")
)

// ============================================================================
// R2025 MECH Professional Electives (3 Verticals)
// Vertical I: Design
// Vertical II: Manufacturing and Automation
// Vertical III: Thermal
// ============================================================================
private val r2025MechProfessionalElectives = listOf(
    // Vertical I - Design
    ElectiveCourse("25MEP01", "Design for X"),
    ElectiveCourse("25MEP02", "CAD/CAM"),
    ElectiveCourse("25MEP03", "Product Life Cycle Management"),
    ElectiveCourse("25MEP04", "Ergonomics in Design"),
    ElectiveCourse("25MEP05", "Automobile Engineering"),
    ElectiveCourse("25MEP06", "Statistical Quality Control"),
    ElectiveCourse("25MEP07", "Dynamics of Ground Vehicles"),
    ElectiveCourse("25MEP08", "Mechanical Vibration"),
    ElectiveCourse("25MEP09", "Artificial Intelligence for Mechanical Engineers"),
    ElectiveCourse("25MEP10", "Process Planning and Cost Estimation"),
    ElectiveCourse("25MEP11", "Industrial Management"),
    ElectiveCourse("25MEP12", "Computer Integrated Manufacturing"),
    // Vertical II - Manufacturing and Automation
    ElectiveCourse("25MEP13", "Casting and Welding Processes"),
    ElectiveCourse("25MEP14", "Selection of Materials"),
    ElectiveCourse("25MEP15", "Additive Manufacturing"),
    ElectiveCourse("25MEP16", "Composite Materials"),
    ElectiveCourse("25MEP17", "Non-traditional Machining Processes"),
    ElectiveCourse("25MEP18", "Mechanical Behaviour of Materials"),
    ElectiveCourse("25MEP19", "Non-Destructive Testing of Materials"),
    ElectiveCourse("25MEP20", "Materials Characterization"),
    ElectiveCourse("25MEP21", "Nanomaterials and Applications"),
    ElectiveCourse("25MEP22", "Industrial Robotics"),
    ElectiveCourse("25MEP23", "Industry 4.0"),
    ElectiveCourse("25MEP24", "Drone Structures and Dynamics"),
    // Vertical III - Thermal
    ElectiveCourse("25MEP25", "Heat Exchanger Design"),
    ElectiveCourse("25MEP26", "Power Plant Engineering"),
    ElectiveCourse("25MEP27", "Renewable Energy"),
    ElectiveCourse("25MEP28", "Fuel Cell Technologies"),
    ElectiveCourse("25MEP29", "Computational Fluid Dynamics"),
    ElectiveCourse("25MEP30", "Hydraulics and Pneumatics"),
    ElectiveCourse("25MEP31", "Electric Vehicle Technology"),
    ElectiveCourse("25MEP32", "Alternate Fuels"),
    ElectiveCourse("25MEP33", "Cryogenic Engineering"),
    ElectiveCourse("25MEP34", "Energy Storage Technologies")
)

// ============================================================================
// R2025 CIVIL Professional Electives (4 Verticals)
// Vertical I: Structural Engineering and Construction Practices
// Vertical II: Environmental and Water Resources Engineering
// Vertical III: Geo-informatics and Geotechnical Engineering
// Vertical IV: Diversified
// ============================================================================
private val r2025CivilProfessionalElectives = listOf(
    // Vertical I - Structural Engineering and Construction Practices
    ElectiveCourse("25CEP01", "Prefabricated Structures"),
    ElectiveCourse("25CEP02", "Finite Element Analysis"),
    ElectiveCourse("25CEP03", "Structural Dynamics and Earthquake Resistant Design"),
    ElectiveCourse("25CEP04", "Prestressed Concrete Structures"),
    ElectiveCourse("25CEP05", "Repair and Rehabilitation of Structures"),
    ElectiveCourse("25CEP06", "Sustainable and Lean Construction"),
    ElectiveCourse("25CEP07", "Advanced Reinforced Concrete Design"),
    ElectiveCourse("25CEP08", "Advanced Concrete Technology"),
    // Vertical II - Environmental and Water Resources Engineering
    ElectiveCourse("25CEP09", "Air Pollution and Control Engineering"),
    ElectiveCourse("25CEP10", "Environmental Impact Assessment"),
    ElectiveCourse("25CEP11", "Industrial Waste Management"),
    ElectiveCourse("25CEP12", "Hydrology and Water Resources Engineering"),
    ElectiveCourse("25CEP13", "Solid Waste Management"),
    ElectiveCourse("25CEP14", "Irrigation Engineering"),
    ElectiveCourse("25CEP15", "Watershed Conservation and Management"),
    ElectiveCourse("25CEP16", "Groundwater Engineering"),
    // Vertical III - Geo-informatics and Geotechnical Engineering
    ElectiveCourse("25CEP17", "Ground Improvement Techniques"),
    ElectiveCourse("25CEP18", "Total Station and GPS Surveying"),
    ElectiveCourse("25CEP19", "Geographic Information Systems"),
    ElectiveCourse("25CEP20", "Remote Sensing Techniques and Applications"),
    ElectiveCourse("25CEP21", "Geosynthetics in Civil Engineering"),
    ElectiveCourse("25CEP22", "Pavement Engineering"),
    ElectiveCourse("25CEP23", "Disaster Management and Mitigation"),
    ElectiveCourse("25CEP24", "Cartography"),
    // Vertical IV - Diversified
    ElectiveCourse("25CEP25", "Airport Docks and Harbour Engineering"),
    ElectiveCourse("25CEP26", "Housing Planning and Management"),
    ElectiveCourse("25CEP27", "Traffic Engineering, Safety and Management"),
    ElectiveCourse("25CEP28", "Smart City Planning and Development"),
    ElectiveCourse("25CEP29", "Design of Energy Efficient Buildings"),
    ElectiveCourse("25CEP30", "Digital Construction Techniques"),
    ElectiveCourse("25CEP31", "Architecture for Civil Engineering"),
    ElectiveCourse("25CEP32", "Environment, Health and Safety")
)

// ============================================================================
// R2025 AIDS Professional Electives (4 Verticals)
// Vertical 1: Full Stack Development
// Vertical 2: Data Science for Intelligence System
// Vertical 3: Computational Intelligence and Machine Learning
// Vertical 4: Emerging Technologies
// ============================================================================
private val r2025AidsProfessionalElectives = listOf(
    // Vertical 1 - Full Stack Development (shared with CSE)
    ElectiveCourse("25CSP01", "Micro Service Architecture"),
    ElectiveCourse("25CSP02", "User Experience Design"),
    ElectiveCourse("25CSP03", "DevOps"),
    ElectiveCourse("25CSP04", "Software Testing and Automation"),
    ElectiveCourse("25CSP05", "Secure Full Stack Development"),
    ElectiveCourse("25CSP06", "MERN Stack"),
    ElectiveCourse("25CSP07", "Agile Methodologies"),
    ElectiveCourse("25CSP08", "Vibe Coding"),
    // Vertical 2 - Data Science for Intelligence System
    ElectiveCourse("25ADP01", "Recommender Systems"),
    ElectiveCourse("25ADP02", "Predictive Analytics"),
    ElectiveCourse("25ADP03", "Smart Systems"),
    ElectiveCourse("25ADP04", "Image and Video Analytics"),
    ElectiveCourse("25ADP05", "Text and Speech Analysis"),
    ElectiveCourse("25ADP06", "Knowledge Discovery"),
    ElectiveCourse("25ADP07", "Data Privacy and Security"),
    ElectiveCourse("25ADP08", "Stream Processing"),
    // Vertical 3 - Computational Intelligence and Machine Learning
    ElectiveCourse("25ADP09", "Natural Language Processing"),
    ElectiveCourse("25ADP10", "MLOps"),
    ElectiveCourse("25ADP11", "AI in Cyber Security"),
    ElectiveCourse("25ADP12", "Conversational AI"),
    ElectiveCourse("25ADP13", "Large Language Model"),
    ElectiveCourse("25ADP14", "Marketing Analytics"),
    ElectiveCourse("25ADP15", "Secure Coding"),
    ElectiveCourse("25ADP16", "Cognitive Science"),
    // Vertical 4 - Emerging Technologies
    ElectiveCourse("25ADP17", "Embedded System and IoT"),
    ElectiveCourse("25CSP13", "Modern Cryptography"),
    ElectiveCourse("25ADP18", "Cloud Services Management"),
    ElectiveCourse("25CSP21", "Blockchain Technologies"),
    ElectiveCourse("25CSP22", "Immersive Technologies"),
    ElectiveCourse("25ADP19", "Quantum Computing"),
    ElectiveCourse("25ADP20", "Responsible AI"),
    ElectiveCourse("25CSP14", "Cyber Forensics")
)
