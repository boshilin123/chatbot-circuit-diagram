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
        
        // 输入框自动聚焦
        this.inputBox.focus();
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
        this.appendMessage('user', message);
        
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
            this.appendMessage('bot', '❌ 后端接口暂未实现，请等待后端开发完成\n\n提示：当前前端已准备就绪，可以开始开发后端接口了！');
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
                break;
                
            case 'options':
                // 选择题
                this.appendMessage('bot', data.content);
                this.appendOptions(data.options);
                break;
                
            case 'result':
                // 最终结果
                this.appendMessage('bot', data.content);
                this.appendResult(data.document);
                break;
                
            default:
                this.appendMessage('bot', '❌ 未知的响应类型: ' + data.type);
        }
    }
    
    /**
     * 添加文本消息
     * @param {string} role - 角色：'user' 或 'bot'
     * @param {string} content - 消息内容
     */
    appendMessage(role, content) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message message-${role}`;
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.textContent = content;
        
        messageDiv.appendChild(contentDiv);
        this.chatArea.appendChild(messageDiv);
        
        // 滚动到底部
        this.scrollToBottom();
    }
    
    /**
     * 添加选择题选项
     * @param {Array} options - 选项数组
     */
    appendOptions(options) {
        if (!options || options.length === 0) {
            return;
        }
        
        const container = document.createElement('div');
        container.className = 'options-container';
        
        options.forEach((option, index) => {
            const button = document.createElement('button');
            button.className = 'option-button';
            button.textContent = `${index + 1}. ${option.text}`;
            
            // 点击选项
            button.addEventListener('click', () => {
                this.selectOption(option, button);
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
     */
    async selectOption(option, button) {
        // 禁用所有选项按钮
        const allButtons = button.parentElement.querySelectorAll('.option-button');
        allButtons.forEach(btn => btn.disabled = true);
        
        // 高亮选中的按钮
        button.style.background = '#667eea';
        button.style.color = 'white';
        
        // 显示用户选择
        this.appendMessage('user', option.text);
        
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
            this.appendMessage('bot', '❌ 后端接口暂未实现');
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
