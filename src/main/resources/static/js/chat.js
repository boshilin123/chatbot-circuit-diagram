/**
 * 智能车辆电路图资料导航 Chatbot - 前端交互逻辑
 * 严格遵守 API 接口文档规范
 */

class ChatApp {
    constructor() {
        // 生成会话ID
        this.sessionId = this.generateSessionId();
        
        // DOM 元素
        this.chatArea = document.getElementById('chatArea');
        this.inputBox = document.getElementById('inputBox');
        this.sendBtn = document.getElementById('sendBtn');
        this.loadingIndicator = document.getElementById('loadingIndicator');
        
        // 历史记录相关DOM元素
        this.historyBtn = document.getElementById('historyBtn');
        this.historySidebar = document.getElementById('historySidebar');
        this.historyOverlay = document.getElementById('historyOverlay');
        this.closeHistoryBtn = document.getElementById('closeHistoryBtn');
        this.clearAllBtn = document.getElementById('clearAllBtn');
        
        // 会话管理相关DOM元素
        this.newSessionBtn = document.getElementById('newSessionBtn');
        this.sessionList = document.getElementById('sessionList');
        
        // 调试信息：检查DOM元素是否正确获取
        console.log('DOM元素检查:');
        console.log('newSessionBtn:', this.newSessionBtn);
        console.log('sessionList:', this.sessionList);
        console.log('historySidebar:', this.historySidebar);
        
        // 会话管理数据
        this.sessions = this.loadSessions();
        this.currentSessionId = this.sessionId;
        this.currentSessionMessages = [];
        
        // 初始化
        this.init();
        
        console.log('ChatApp 初始化完成，会话ID:', this.sessionId);
    }
    
    /**
     * 初始化事件监听
     */
    init() {
        // 发送按钮点击事件
        this.sendBtn.addEventListener('click', () => this.sendMessage());
        
        // 输入框回车事件
        this.inputBox.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });
        
        // 历史记录相关事件
        this.historyBtn.addEventListener('click', () => this.openHistory());
        this.closeHistoryBtn.addEventListener('click', () => this.closeHistory());
        this.historyOverlay.addEventListener('click', () => this.closeHistory());
        this.clearAllBtn.addEventListener('click', () => this.clearAllData());
        
        // 会话管理相关事件
        if (this.newSessionBtn) {
            this.newSessionBtn.addEventListener('click', () => this.createNewSession());
            console.log('新建会话按钮事件监听已添加');
        } else {
            console.error('新建会话按钮未找到！');
        }
        
        // 初始化显示
        this.renderSessions();
        
        // 添加示例查询点击事件
        this.addExampleQueryListeners();
        
        // 输入框自动聚焦
        this.inputBox.focus();
    }
    
    /**
     * 添加示例查询点击事件
     */
    addExampleQueryListeners() {
        // 等待DOM加载完成后添加事件监听
        setTimeout(() => {
            const exampleTags = document.querySelectorAll('.example-tag');
            exampleTags.forEach(tag => {
                tag.addEventListener('click', () => {
                    const query = tag.textContent.trim();
                    this.inputBox.value = query;
                    this.inputBox.focus();
                    // 自动发送查询
                    this.sendMessage();
                });
            });
        }, 100);
    }
    
    /**
     * 发送消息
     */
    async sendMessage() {
        const message = this.inputBox.value.trim();
        
        // 验证输入
        if (!message) {
            alert('请输入查询内容');
            return;
        }
        
        // 显示用户消息
        this.appendMessage('user', message, true); // 第三个参数true表示保存到会话
        
        // 清空输入框
        this.inputBox.value = '';
        
        // 禁用发送按钮
        this.setLoading(true);
        
        try {
            // 调用 API（遵守接口文档规范）
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    sessionId: this.sessionId,
                    message: message
                })
            });
            
            // 解析响应
            const result = await response.json();
            
            console.log('API 响应:', result);
            
            // 检查业务状态码（遵守 Result<T> 规范）
            if (result.code === 1) {
                // 成功：处理业务数据
                this.handleResponse(result.data);
            } else {
                // 失败：显示错误信息
                this.appendMessage('bot', '❌ ' + result.msg);
            }
            
        } catch (error) {
            console.error('请求失败:', error);
            // 接口已实现，不显示错误提示
        } finally {
            // 恢复发送按钮
            this.setLoading(false);
            this.inputBox.focus();
        }
    }
    
    /**
     * 处理 API 响应数据
     * @param {Object} data - ChatResponseData 对象
     */
    handleResponse(data) {
        if (!data) {
            this.appendMessage('bot', '❌ 响应数据为空');
            return;
        }
        
        // 根据响应类型处理
        switch (data.type) {
            case 'text':
                // 文本消息
                this.appendMessage('bot', data.content);
                // 保存完整的消息数据
                this.saveMessageToSession('bot', data.content, 'text', null);
                break;
                
            case 'options':
                // 选择题
                this.appendMessage('bot', data.content);
                this.appendOptions(data.options);
                // 保存完整的消息数据（包括选项）
                this.saveMessageToSession('bot', data.content, 'options', { options: data.options });
                break;
                
            case 'result':
                // 最终结果
                this.appendMessage('bot', data.content);
                this.appendResult(data.document);
                // 保存完整的消息数据（包括文档）
                this.saveMessageToSession('bot', data.content, 'result', { document: data.document });
                break;
                
            default:
                this.appendMessage('bot', '❌ 未知的响应类型: ' + data.type);
        }
    }
    
    /**
     * 添加文本消息
     * @param {string} role - 角色：'user' 或 'bot'
     * @param {string} content - 消息内容
     * @param {boolean} saveToSession - 是否保存到会话（默认false，因为通常在handleResponse中已保存）
     */
    appendMessage(role, content, saveToSession = false) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message message-${role}`;
        
        // 添加头像
        const avatarDiv = document.createElement('div');
        avatarDiv.className = `avatar ${role}-avatar`;
        const avatarIcon = document.createElement('span');
        avatarIcon.className = 'avatar-icon';
        avatarIcon.textContent = role === 'bot' ? '🤖' : '👤';
        avatarDiv.appendChild(avatarIcon);
        messageDiv.appendChild(avatarDiv);
        
        // 消息内容容器
        const contentContainer = document.createElement('div');
        contentContainer.className = 'message-content';
        
        // 消息头部（发送者和时间）
        const headerDiv = document.createElement('div');
        headerDiv.className = 'message-header';
        
        const senderName = document.createElement('span');
        senderName.className = 'sender-name';
        senderName.textContent = role === 'bot' ? '智能助手' : '您';
        
        const messageTime = document.createElement('span');
        messageTime.className = 'message-time';
        messageTime.textContent = new Date().toLocaleTimeString('zh-CN', { 
            hour: '2-digit', 
            minute: '2-digit' 
        });
        
        headerDiv.appendChild(senderName);
        headerDiv.appendChild(messageTime);
        
        // 消息文本
        const textDiv = document.createElement('div');
        textDiv.className = 'message-text';
        
        // 处理HTML内容（支持换行和格式化）
        if (content.includes('<br>') || content.includes('<strong>') || content.includes('<div>') || content.includes('<span>')) {
            textDiv.innerHTML = content;
        } else {
            textDiv.textContent = content;
        }
        
        contentContainer.appendChild(headerDiv);
        contentContainer.appendChild(textDiv);
        messageDiv.appendChild(contentContainer);
        
        this.chatArea.appendChild(messageDiv);
        
        // 只有在明确指定时才保存到会话（用于用户消息）
        if (saveToSession) {
            this.saveMessageToSession(role, content, 'text', null);
        }
        
        // 滚动到底部
        this.scrollToBottom();
    }
    
    /**
     * 保存消息到当前会话
     * @param {string} role - 角色
     * @param {string} content - 内容
     * @param {string} type - 消息类型：'text', 'options', 'result'
     * @param {Object} data - 额外数据（选项列表或文档信息）
     */
    saveMessageToSession(role, content, type = 'text', data = null) {
        const message = {
            role: role,
            content: content,
            type: type,
            data: data,
            timestamp: new Date().toISOString()
        };
        
        this.currentSessionMessages.push(message);
        this.updateCurrentSession();
    }
    
    /**
     * 更新当前会话
     */
    updateCurrentSession() {
        const sessionIndex = this.sessions.findIndex(s => s.id === this.currentSessionId);
        
        if (sessionIndex >= 0) {
            // 更新现有会话
            this.sessions[sessionIndex].messages = [...this.currentSessionMessages];
            this.sessions[sessionIndex].lastMessage = this.getLastUserMessage();
            this.sessions[sessionIndex].updatedAt = new Date().toISOString();
        } else {
            // 创建新会话
            const newSession = {
                id: this.currentSessionId,
                title: this.generateSessionTitle(),
                messages: [...this.currentSessionMessages],
                lastMessage: this.getLastUserMessage(),
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString()
            };
            this.sessions.unshift(newSession);
        }
        
        // 限制会话数量（最多保存20个会话）
        if (this.sessions.length > 20) {
            this.sessions = this.sessions.slice(0, 20);
        }
        
        this.saveSessions();
        this.renderSessions();
    }
    
    /**
     * 生成会话标题
     */
    generateSessionTitle() {
        const userMessages = this.currentSessionMessages.filter(m => m.role === 'user');
        if (userMessages.length > 0) {
            const firstMessage = userMessages[0].content;
            return firstMessage.length > 20 ? firstMessage.substring(0, 20) + '...' : firstMessage;
        }
        return '新会话';
    }
    
    /**
     * 获取最后一条用户消息
     */
    getLastUserMessage() {
        const userMessages = this.currentSessionMessages.filter(m => m.role === 'user');
        return userMessages.length > 0 ? userMessages[userMessages.length - 1].content : '';
    }
    
    /**
     * 创建新会话
     */
    createNewSession() {
        console.log('创建新会话被调用');
        
        // 保存当前会话
        if (this.currentSessionMessages.length > 0) {
            this.updateCurrentSession();
            console.log('当前会话已保存，消息数量:', this.currentSessionMessages.length);
        }
        
        // 创建新会话
        this.sessionId = this.generateSessionId();
        this.currentSessionId = this.sessionId;
        this.currentSessionMessages = [];
        
        console.log('新会话ID:', this.currentSessionId);
        
        // 清空聊天区域，显示欢迎消息
        this.chatArea.innerHTML = `
            <div class="message message-bot">
                <div class="avatar bot-avatar">
                    <span class="avatar-icon">🤖</span>
                </div>
                <div class="message-content">
                    <div class="message-header">
                        <span class="sender-name">智能助手</span>
                        <span class="message-time">刚刚</span>
                    </div>
                    <div class="message-text">
                        🎉 <strong>新会话已创建！</strong><br><br>
                        您好！我是智能车辆电路图资料导航助手 ✨<br><br>
                        🎯 拥有 <strong>4000+</strong> 条电路图资料，采用智能搜索技术<br>
                        ⚡ 简单查询秒级响应，复杂问题AI理解<br>
                        📋 支持历史记录，方便随时查看<br><br>
                        💡 <strong>推荐简单搜索</strong>（响应更快）：<br>
                        <div class="example-queries">
                            <span class="example-tag">东风天龙仪表</span>
                            <span class="example-tag">红岩杰狮保险丝</span>
                            <span class="example-tag">三一4HK1</span>
                        </div>
                        请输入您要查找的内容，我来帮您快速定位！😊
                    </div>
                </div>
            </div>
        `;
        
        // 重新添加示例查询点击事件
        this.addExampleQueryListeners();
        
        // 更新会话列表显示
        this.renderSessions();
        
        // 关闭侧边栏
        this.closeHistory();
        
        // 聚焦输入框
        this.inputBox.focus();
        
        // 显示成功提示
        this.showNotification('✅ 新会话已创建！', 'success');
        
        console.log('新会话创建完成:', this.currentSessionId);
    }
    
    /**
     * 切换到指定会话
     */
    switchToSession(sessionId) {
        // 保存当前会话
        if (this.currentSessionMessages.length > 0) {
            this.updateCurrentSession();
        }
        
        // 查找目标会话
        const targetSession = this.sessions.find(s => s.id === sessionId);
        if (!targetSession) {
            console.error('会话不存在:', sessionId);
            return;
        }
        
        // 切换会话
        this.sessionId = sessionId;
        this.currentSessionId = sessionId;
        this.currentSessionMessages = [...targetSession.messages];
        
        // 重建聊天界面
        this.rebuildChatArea(targetSession.messages);
        
        // 更新会话列表显示
        this.renderSessions();
        
        // 关闭侧边栏
        this.closeHistory();
        
        // 聚焦输入框
        this.inputBox.focus();
        
        console.log('切换到会话:', sessionId);
    }
    
    /**
     * 重建聊天区域
     */
    rebuildChatArea(messages) {
        this.chatArea.innerHTML = '';
        
        messages.forEach(message => {
            this.appendMessageFromHistory(message);
        });
        
        // 重新添加示例查询点击事件
        this.addExampleQueryListeners();
    }
    
    /**
     * 从历史记录添加消息（支持完整的消息类型）
     * @param {Object} message - 消息对象
     */
    appendMessageFromHistory(message) {
        const { role, content, type = 'text', data = null } = message;
        
        // 添加文本消息
        const messageDiv = document.createElement('div');
        messageDiv.className = `message message-${role}`;
        
        // 添加头像
        const avatarDiv = document.createElement('div');
        avatarDiv.className = `avatar ${role}-avatar`;
        const avatarIcon = document.createElement('span');
        avatarIcon.className = 'avatar-icon';
        avatarIcon.textContent = role === 'bot' ? '🤖' : '👤';
        avatarDiv.appendChild(avatarIcon);
        messageDiv.appendChild(avatarDiv);
        
        // 消息内容容器
        const contentContainer = document.createElement('div');
        contentContainer.className = 'message-content';
        
        // 消息头部（发送者和时间）
        const headerDiv = document.createElement('div');
        headerDiv.className = 'message-header';
        
        const senderName = document.createElement('span');
        senderName.className = 'sender-name';
        senderName.textContent = role === 'bot' ? '智能助手' : '您';
        
        const messageTime = document.createElement('span');
        messageTime.className = 'message-time';
        messageTime.textContent = new Date(message.timestamp).toLocaleTimeString('zh-CN', { 
            hour: '2-digit', 
            minute: '2-digit' 
        });
        
        headerDiv.appendChild(senderName);
        headerDiv.appendChild(messageTime);
        
        // 消息文本
        const textDiv = document.createElement('div');
        textDiv.className = 'message-text';
        
        // 处理HTML内容
        if (content.includes('<br>') || content.includes('<strong>') || content.includes('<div>') || content.includes('<span>')) {
            textDiv.innerHTML = content;
        } else {
            textDiv.textContent = content;
        }
        
        contentContainer.appendChild(headerDiv);
        contentContainer.appendChild(textDiv);
        messageDiv.appendChild(contentContainer);
        
        this.chatArea.appendChild(messageDiv);
        
        // 根据消息类型添加额外内容
        if (type === 'options' && data && data.options) {
            // 恢复选项列表
            this.appendOptionsFromHistory(data.options);
        } else if (type === 'result' && data && data.document) {
            // 恢复结果文档
            this.appendResultFromHistory(data.document);
        }
    }
    
    /**
     * 从历史记录恢复选项列表
     */
    appendOptionsFromHistory(options) {
        const optionsDiv = document.createElement('div');
        optionsDiv.className = 'options-container';
        
        const letters = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
        
        options.forEach((option, index) => {
            const button = document.createElement('button');
            button.className = 'option-button';
            const letter = letters[index] || (index + 1);
            button.textContent = `${letter}. ${option.text}`;
            
            // 历史记录中的选项不可点击
            button.disabled = true;
            button.style.opacity = '0.7';
            button.style.cursor = 'not-allowed';
            button.style.background = '#f0f0f0';
            button.style.color = '#999';
            button.style.borderColor = '#ddd';
            
            optionsDiv.appendChild(button);
        });
        
        this.chatArea.appendChild(optionsDiv);
    }
    
    /**
     * 从历史记录恢复结果文档
     */
    appendResultFromHistory(document) {
        if (!document) {
            return;
        }
        
        const container = document.createElement('div');
        container.className = 'result-container';
        
        // 标题
        const title = document.createElement('div');
        title.className = 'result-title';
        title.textContent = '📄 查询结果';
        container.appendChild(title);
        
        // ID
        const idItem = document.createElement('div');
        idItem.className = 'result-item';
        idItem.innerHTML = `<span class="result-label">文档ID：</span>${document.id}`;
        container.appendChild(idItem);
        
        // 层级路径
        const pathItem = document.createElement('div');
        pathItem.className = 'result-item';
        pathItem.innerHTML = `<span class="result-label">层级路径：</span>${document.hierarchyPath || '未知'}`;
        container.appendChild(pathItem);
        
        // 文件名称
        const nameItem = document.createElement('div');
        nameItem.className = 'result-item';
        nameItem.innerHTML = `<span class="result-label">文件名称：</span>${document.fileName}`;
        container.appendChild(nameItem);
        
        // 关键词（如果有）
        if (document.keywords && document.keywords.length > 0) {
            const keywordsItem = document.createElement('div');
            keywordsItem.className = 'result-item';
            keywordsItem.innerHTML = `<span class="result-label">关键词：</span>${document.keywords.join(', ')}`;
            container.appendChild(keywordsItem);
        }
        
        this.chatArea.appendChild(container);
    }
    
    /**
     * 删除会话
     */
    deleteSession(sessionId) {
        if (confirm('确定要删除这个会话吗？')) {
            this.sessions = this.sessions.filter(s => s.id !== sessionId);
            this.saveSessions();
            this.renderSessions();
            
            // 如果删除的是当前会话，创建新会话
            if (sessionId === this.currentSessionId) {
                this.createNewSession();
            }
        }
    }
    
    /**
     * 加载会话数据
     */
    loadSessions() {
        try {
            const sessions = localStorage.getItem('chatbot_sessions');
            return sessions ? JSON.parse(sessions) : [];
        } catch (error) {
            console.error('加载会话数据失败:', error);
            return [];
        }
    }
    
    /**
     * 保存会话数据
     */
    saveSessions() {
        try {
            localStorage.setItem('chatbot_sessions', JSON.stringify(this.sessions));
        } catch (error) {
            console.error('保存会话数据失败:', error);
        }
    }
    
    /**
     * 渲染会话列表
     */
    renderSessions() {
        if (this.sessions.length === 0) {
            this.sessionList.innerHTML = `
                <div class="session-empty">
                    <div class="empty-icon">💭</div>
                    <p>暂无历史会话</p>
                    <small>开始对话后，会话记录会显示在这里</small>
                </div>
            `;
            return;
        }
        
        const sessionsHtml = this.sessions.map(session => `
            <div class="session-item ${session.id === this.currentSessionId ? 'active' : ''}" 
                 data-session-id="${session.id}">
                <div class="session-title">${session.title}</div>
                <div class="session-time">${new Date(session.updatedAt).toLocaleString('zh-CN')}</div>
                <div class="session-preview">${session.lastMessage}</div>
                <button class="session-delete" data-session-id="${session.id}">×</button>
            </div>
        `).join('');
        
        this.sessionList.innerHTML = sessionsHtml;
        
        // 添加点击事件
        this.sessionList.querySelectorAll('.session-item').forEach(item => {
            item.addEventListener('click', (e) => {
                if (!e.target.classList.contains('session-delete')) {
                    const sessionId = item.dataset.sessionId;
                    this.switchToSession(sessionId);
                }
            });
        });
        
        // 添加删除事件
        this.sessionList.querySelectorAll('.session-delete').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const sessionId = btn.dataset.sessionId;
                this.deleteSession(sessionId);
            });
        });
    }
    
    /**
     * 添加选择题选项
     * 使用字母编号：A. B. C. D. E.
     * @param {Array} options - 选项数组
     */
    appendOptions(options) {
        if (!options || options.length === 0) {
            return;
        }
        
        const container = document.createElement('div');
        container.className = 'options-container';
        
        const letters = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
        
        options.forEach((option, index) => {
            const button = document.createElement('button');
            button.className = 'option-button';
            const letter = letters[index] || (index + 1);
            button.textContent = `${letter}. ${option.text}`;
            
            // 点击选项
            button.addEventListener('click', () => {
                this.selectOption(option, button, letter);
            });
            
            container.appendChild(button);
        });
        
        this.chatArea.appendChild(container);
        this.scrollToBottom();
    }
    
    /**
     * 处理用户选择
     * @param {Object} option - 选项对象
     * @param {HTMLElement} button - 按钮元素
     * @param {string} letter - 选项字母
     */
    async selectOption(option, button, letter) {
        // 重置同组所有按钮的状态（允许重新选择）
        const allButtons = button.parentElement.querySelectorAll('.option-button');
        allButtons.forEach(btn => {
            btn.disabled = false;
            btn.style.background = 'white';
            btn.style.color = '#667eea';
        });
        
        // 高亮选中的按钮
        button.style.background = '#667eea';
        button.style.color = 'white';
        
        // 显示用户选择（只显示字母）
        this.appendMessage('user', letter, true); // 保存用户选择
        
        // 显示加载状态
        this.setLoading(true);
        
        try {
            // 调用选择接口（遵守接口文档规范）
            const response = await fetch('/api/select', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    sessionId: this.sessionId,
                    optionId: option.id,
                    optionValue: option.value
                })
            });
            
            const result = await response.json();
            
            console.log('选择响应:', result);
            
            // 检查业务状态码
            if (result.code === 1) {
                this.handleResponse(result.data);
            } else {
                this.appendMessage('bot', '❌ ' + result.msg);
            }
            
        } catch (error) {
            console.error('选择失败:', error);
            // 接口已实现，不显示错误提示
        } finally {
            this.setLoading(false);
        }
    }
    
    /**
     * 显示最终结果
     * @param {Object} document - CircuitDocument 对象
     */
    appendResult(document) {
        if (!document) {
            return;
        }
        
        const container = document.createElement('div');
        container.className = 'result-container';
        
        // 标题
        const title = document.createElement('div');
        title.className = 'result-title';
        title.textContent = '📄 查询结果';
        container.appendChild(title);
        
        // ID
        const idItem = document.createElement('div');
        idItem.className = 'result-item';
        idItem.innerHTML = `<span class="result-label">文档ID：</span>${document.id}`;
        container.appendChild(idItem);
        
        // 层级路径
        const pathItem = document.createElement('div');
        pathItem.className = 'result-item';
        pathItem.innerHTML = `<span class="result-label">层级路径：</span>${document.hierarchyPath}`;
        container.appendChild(pathItem);
        
        // 文件名称
        const nameItem = document.createElement('div');
        nameItem.className = 'result-item';
        nameItem.innerHTML = `<span class="result-label">文件名称：</span>${document.fileName}`;
        container.appendChild(nameItem);
        
        // 关键词（如果有）
        if (document.keywords && document.keywords.length > 0) {
            const keywordsItem = document.createElement('div');
            keywordsItem.className = 'result-item';
            keywordsItem.innerHTML = `<span class="result-label">关键词：</span>${document.keywords.join(', ')}`;
            container.appendChild(keywordsItem);
        }
        
        this.chatArea.appendChild(container);
        this.scrollToBottom();
    }
    
    /**
     * 滚动到底部
     */
    scrollToBottom() {
        setTimeout(() => {
            this.chatArea.scrollTop = this.chatArea.scrollHeight;
        }, 100);
    }
    
    /**
     * 设置加载状态
     * @param {boolean} loading - 是否加载中
     */
    setLoading(loading) {
        this.sendBtn.disabled = loading;
        this.loadingIndicator.style.display = loading ? 'flex' : 'none';
    }
    
    /**
     * 打开历史记录侧边栏
     */
    openHistory() {
        this.historySidebar.classList.add('open');
        this.historyOverlay.classList.add('show');
        document.body.style.overflow = 'hidden';
    }
    
    /**
     * 关闭历史记录侧边栏
     */
    closeHistory() {
        this.historySidebar.classList.remove('open');
        this.historyOverlay.classList.remove('show');
        document.body.style.overflow = '';
    }
    
    /**
     * 显示通知消息
     * @param {string} message - 通知消息
     * @param {string} type - 通知类型：success, error, info
     */
    showNotification(message, type = 'info') {
        // 创建通知元素
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        notification.innerHTML = `
            <div class="notification-content">
                <span class="notification-message">${message}</span>
                <button class="notification-close">×</button>
            </div>
        `;
        
        // 添加样式
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: ${type === 'success' ? '#28a745' : type === 'error' ? '#dc3545' : '#667eea'};
            color: white;
            padding: 15px 20px;
            border-radius: 10px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.2);
            z-index: 10000;
            animation: slideInRight 0.3s ease-out;
            max-width: 300px;
        `;
        
        // 添加到页面
        document.body.appendChild(notification);
        
        // 关闭按钮事件
        const closeBtn = notification.querySelector('.notification-close');
        closeBtn.addEventListener('click', () => {
            notification.remove();
        });
        
        // 自动关闭
        setTimeout(() => {
            if (notification.parentNode) {
                notification.style.animation = 'slideOutRight 0.3s ease-out';
                setTimeout(() => notification.remove(), 300);
            }
        }, 3000);
    }
    
    /**
     * 清空所有会话记录
     */
    clearAllData() {
        if (confirm('确定要清空所有会话记录吗？')) {
            // 清空会话记录
            this.sessions = [];
            localStorage.removeItem('chatbot_sessions');
            
            // 重新渲染
            this.renderSessions();
            
            // 创建新会话
            this.createNewSession();
            
            console.log('所有会话记录已清空');
        }
    }
    
    /**
     * 生成会话ID
     * @returns {string} 会话ID
     */
    generateSessionId() {
        const timestamp = Date.now();
        const random = Math.random().toString(36).substring(2, 11);
        return `session_${timestamp}_${random}`;
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
    console.log('页面加载完成，初始化 ChatApp...');
    window.chatApp = new ChatApp();
});
